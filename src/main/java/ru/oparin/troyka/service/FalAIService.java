package ru.oparin.troyka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.config.properties.FalAiProperties;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.exception.FalAIException;
import ru.oparin.troyka.model.dto.fal.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static ru.oparin.troyka.util.JsonUtils.removingBlob;

/**
 * Сервис для работы с FAL AI API.
 * Предоставляет методы для генерации и редактирования изображений с поддержкой сессий.
 */
@Slf4j
@Service
public class FalAIService {
    public static final String PREFIX_PATH = "/fal-ai/";

    private final WebClient webClient;
    private final FalAiProperties prop;
    private final GenerationProperties generationProperties;
    private final ImageGenerationHistoryService imageGenerationHistoryService;
    private final UserPointsService userPointsService;
    private final SessionService sessionService;
    private final ArtStyleService artStyleService;

    public FalAIService(WebClient.Builder webClientBuilder,
                        FalAiProperties falAiProperties,
                        GenerationProperties generationProperties,
                        ImageGenerationHistoryService imageGenerationHistoryService,
                        UserPointsService userPointsService,
                        SessionService sessionService,
                        ArtStyleService artStyleService) {
        this.webClient = webClientBuilder
                .baseUrl(falAiProperties.getApi().getUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Key " + falAiProperties.getApi().getKey())
                .build();
        this.prop = falAiProperties;
        this.generationProperties = generationProperties;
        this.imageGenerationHistoryService = imageGenerationHistoryService;
        this.userPointsService = userPointsService;
        this.sessionService = sessionService;
        this.artStyleService = artStyleService;
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
        Integer pointsNeeded = numImages * generationProperties.getPointsPerImage();

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

                                                                FalAIRequestDTO requestBody = createRqBody(imageRq, finalPrompt, numImages, inputImageUrls);

                                                                boolean isNewImage = CollectionUtils.isEmpty(inputImageUrls);
                                                                String model = isNewImage ? prop.getModel().getCreate() : prop.getModel().getEdit();
                                                                String fullModelPath = PREFIX_PATH + model;
                                                                String fullUrl = prop.getApi().getUrl() + fullModelPath;
                                                                String modelType = isNewImage ? "создание" : "редактирование";
                                                                log.info("Будет отправлен запрос в fal.ai на {} изображений по адресу '{}' с телом '{}'", modelType, fullUrl, requestBody);

                                                                return webClient.post()
                                                                        .uri(fullModelPath)
                                                                        .bodyValue(requestBody)
                                                                        .retrieve()
                                                                        .bodyToMono(new ParameterizedTypeReference<FalAIResponseDTO>() {
                                                                        })
                                                                        .timeout(Duration.ofMinutes(3))
                                                                        .map(response -> extractImageResponse(response, balance))
                                                                         .flatMap(response -> imageGenerationHistoryService.saveHistories(
                                                                                         userId, response.getImageUrls(), userPrompt,
                                                                                         session.getId(), inputImageUrls, response.getDescription(), styleId, imageRq.getAspectRatio())
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
     * Тело сообщения, которое будет отправлено в fal.ai
     */
    private FalAIRequestDTO createRqBody(ImageRq rq, String prompt, Integer numImages, List<String> inputImageUrls) {
        FalAIRequestDTO.FalAIRequestDTOBuilder builder = FalAIRequestDTO.builder()
                .prompt(prompt)
                .numImages(numImages)
                .outputFormat(rq.getOutputFormat().name().toLowerCase())
                .aspectRatio(rq.getAspectRatio());

        if (!CollectionUtils.isEmpty(inputImageUrls)) {
            builder.imageUrls(removingBlob(inputImageUrls));
        }

        return builder.build();
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
        String description = response.getDescription();

        List<String> urls = response.getImages().stream()
                .map(FalAIImageDTO::getUrl)
                .toList();

        return new ImageRs(description, urls, balance);
    }
}