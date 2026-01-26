package ru.oparin.troyka.service;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import ru.oparin.troyka.config.properties.FalAiProperties;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.exception.FalAIException;
import ru.oparin.troyka.mapper.FalAIQueueMapper;
import ru.oparin.troyka.model.dto.fal.*;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.model.enums.Resolution;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Сервис для работы с FAL AI API.
 * Предоставляет методы для генерации и редактирования изображений с поддержкой сессий.
 */
@Slf4j
@Service
public class FalAIService {
    public static final String PREFIX_PATH = "/fal-ai/";

    private final WebClient webClient;
    private final GenerationProperties generationProperties;
    private final ImageGenerationHistoryService imageGenerationHistoryService;
    private final UserPointsService userPointsService;
    private final SessionService sessionService;
    private final ArtStyleService artStyleService;
    private final FalAIQueueMapper mapper;

    public FalAIService(WebClient.Builder webClientBuilder,
                        FalAiProperties falAiProperties,
                        GenerationProperties generationProperties,
                        ImageGenerationHistoryService imageGenerationHistoryService,
                        UserPointsService userPointsService,
                        SessionService sessionService,
                        ArtStyleService artStyleService,
                        FalAIQueueMapper mapper) {
        // Создаем отдельный HttpClient для FAL AI с увеличенным таймаутом (5 минут)
        HttpClient falAiHttpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofMinutes(5))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.MINUTES))
                                .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.MINUTES)));

        this.webClient = webClientBuilder
                .baseUrl(falAiProperties.getApi().getUrl())
                .clientConnector(new ReactorClientHttpConnector(falAiHttpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Key " + falAiProperties.getApi().getKey())
                .build();
        this.generationProperties = generationProperties;
        this.imageGenerationHistoryService = imageGenerationHistoryService;
        this.userPointsService = userPointsService;
        this.sessionService = sessionService;
        this.artStyleService = artStyleService;
        this.mapper = mapper;
    }

    /**
     * Получить ответ с изображениями от FAL AI с поддержкой сессий.
     * Автоматически создает или получает дефолтную сессию, если sessionId не указан.
     *
     * @param imageRq запрос на генерацию изображения
     * @param userId  идентификатор пользователя
     * @return ответ с сгенерированными изображениями
     */
    public Mono<ImageRs> getImageResponse(ImageRq imageRq, Long userId) {
        Integer numImages = imageRq.getNumImages();
        GenerationModelType modelType = imageRq.getModel();
        Resolution resolution = imageRq.getResolution();
        Integer pointsNeeded = generationProperties.getPointsNeeded(modelType, resolution, numImages);

        return userPointsService.hasEnoughPoints(userId, pointsNeeded)
                .flatMap(hasEnough -> {
                    if (!hasEnough) {
                        return Mono.error(new FalAIException(
                                "Недостаточно поинтов для генерации изображений. Требуется: " + pointsNeeded, HttpStatus.PAYMENT_REQUIRED));
                    } else {
                        return sessionService.getOrCreateSession(imageRq.getSessionId(), userId)
                                .flatMap(session -> {
                                    Long styleId = imageRq.getStyleId();
                                    return artStyleService.getStyleById(styleId)
                                            .flatMap(style ->
                                                    userPointsService.deductPointsFromUser(userId, pointsNeeded)
                                                            .flatMap(userPoints -> Mono.defer(() -> {
                                                                Integer balance = userPoints.getPoints();
                                                                String userPrompt = imageRq.getPrompt();
                                                                String finalPrompt = userPrompt + ", " + style.getPrompt();
                                                                List<String> inputImageUrls = imageRq.getInputImageUrls();

                                                                boolean isNewImage = CollectionUtils.isEmpty(inputImageUrls);
                                                                FalAIRequestDTO requestBody = mapper.createRqBody(imageRq, finalPrompt, numImages, inputImageUrls, resolution);

                                                                String modelEndpoint = modelType.getEndpoint(isNewImage);
                                                                String fullModelPath = PREFIX_PATH + modelEndpoint;
                                                                String operationType = isNewImage ? "создание" : "редактирование";
                                                                log.info("Будет отправлен запрос в fal.ai на {} изображений по адресу '{}' с телом '{}'", operationType, fullModelPath, requestBody);

                                                                return webClient.post()
                                                                        .uri(fullModelPath)
                                                                        .bodyValue(requestBody)
                                                                        .retrieve()
                                                                        .bodyToMono(new ParameterizedTypeReference<FalAIResponseDTO>() {
                                                                        })
                                                                        .timeout(Duration.ofMinutes(5))
                                                                        .map(response -> extractImageResponse(response, balance))
                                                                         .flatMap(response -> imageGenerationHistoryService.saveHistories(
                                                                                         userId, response.getImageUrls(), userPrompt,
                                                                                         session.getId(), inputImageUrls, styleId, imageRq.getAspectRatio(),
                                                                                         modelType, resolution, GenerationProvider.FAL_AI)
                                                                                 .then(Mono.just(response)))
                                                                        .flatMap(response -> sessionService.updateSessionTimestamp(session.getId())
                                                                                .then(Mono.just(response)))
                                                                        .doOnSuccess(response -> log.info("Успешно получен ответ с изображением для сессии {}: {}", session.getId(), response))
                                                                        .onErrorResume(e -> exceptionHandling(userId, e, pointsNeeded));
                                                            })));
                                                });
                                    }
                                });
    }

    /**
     * Обработка исключений
     */
    private Mono<ImageRs> exceptionHandling(Long userId, Throwable e, Integer pointsNeeded) {
        log.error("Ошибка при работе с fal.ai для userId={}, pointsNeeded={}: {}", userId, pointsNeeded, e.getMessage(), e);

        if (e instanceof FalAIException) {
            // Уже наш кастомный эксепшн — возврат поинтов уже был
            return Mono.error(e);
        } else if (e instanceof TimeoutException ||
                (e.getCause() != null && e.getCause() instanceof TimeoutException) ||
                (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout"))) {
            log.warn("Timeout при запросе к fal.ai для userId={}. Возвращаем поинты.", userId);
            return userPointsService.addPointsToUser(userId, pointsNeeded)
                    .then(Mono.error(new FalAIException(
                            "Превышено время ожидания ответа от сервиса генерации. Попробуйте позже.",
                            HttpStatus.REQUEST_TIMEOUT)));
        } else if (e instanceof WebClientRequestException) {
            log.warn("Ошибка подключения к fal.ai для userId={}. Возвращаем поинты.", userId);
            return userPointsService.addPointsToUser(userId, pointsNeeded)
                    .then(Mono.error(new FalAIException(
                            "Не удалось подключиться к сервису генерации. Проверьте интернет или попробуйте позже.",
                            HttpStatus.SERVICE_UNAVAILABLE)));
        } else if (e instanceof WebClientResponseException webE) {
            String responseBody = webE.getResponseBodyAsString();
            log.warn("Ошибка от fal.ai для userId={}. Статус: {}, тело: {}. Возвращаем поинты.",
                    userId, webE.getStatusCode(), responseBody);
            String message = "Сервис генерации вернул ошибку. Статус: " + webE.getStatusCode()
                    + ", причина: " + webE.getStatusText();
            if (responseBody != null && !responseBody.isEmpty()) {
                message += ", тело ответа: " + responseBody;
            }
            return userPointsService.addPointsToUser(userId, pointsNeeded)
                    .then(Mono.error(new FalAIException(message, HttpStatus.UNPROCESSABLE_ENTITY)));
        } else {
            log.warn("Неизвестная ошибка при работе с fal.ai для userId={}. Возвращаем поинты.", userId);
            return userPointsService.addPointsToUser(userId, pointsNeeded)
                    .then(Mono.error(new FalAIException(
                            "Произошла ошибка при работе с сервисом генерации: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR)));
        }
    }

    /**
     * Извлечь данные изображений из ответа FAL AI.
     *
     * @param response ответ от FAL AI
     * @return структурированный ответ с изображениями
     */
    private ImageRs extractImageResponse(FalAIResponseDTO response, Integer balance) {
        log.info("Получен ответ: {}", response);

        List<String> urls = response.getImages().stream()
                .map(FalAIImageDTO::getUrl)
                .toList();

        return new ImageRs(urls, balance);
    }
}