package ru.oparin.troyka.service.provider;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.config.properties.LaoZhangProperties;
import ru.oparin.troyka.exception.FalAIException;
import ru.oparin.troyka.mapper.LaoZhangMapper;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.dto.fal.ImageRs;
import ru.oparin.troyka.model.dto.laozhang.LaoZhangResponseDTO;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.model.enums.Resolution;
import ru.oparin.troyka.service.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Провайдер генерации изображений через LaoZhang AI.
 * Использует OpenAI-compatible API формат.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LaoZhangProvider implements ImageGenerationProvider {

    // Используем Google Native Format endpoint для поддержки 4K и кастомных соотношений сторон
    // Endpoint: /v1beta/models/{model}:generateContent
    private static final String ENDPOINT_TEMPLATE = "/v1beta/models/%s:generateContent";

    private final WebClient.Builder webClientBuilder;
    private final LaoZhangProperties laoZhangProperties;
    private final GenerationProperties generationProperties;
    private final LaoZhangMapper laoZhangMapper;
    private final Base64ImageService base64ImageService;
    private final UserPointsService userPointsService;
    private final SessionService sessionService;
    private final ArtStyleService artStyleService;
    private final ImageGenerationHistoryService imageGenerationHistoryService;
    private final UserService userService;

    private WebClient webClient;

    /**
     * Инициализация WebClient для LaoZhang API.
     * Настроен с увеличенными таймаутами для поддержки генерации 4K (до 10 минут).
     */
    private WebClient getWebClient() {
        if (webClient == null) {
            String baseUrl = laoZhangProperties.getApi().getUrl();
            String apiKey = laoZhangProperties.getApi().getKey();

            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("LaoZhang API ключ не настроен");
            }

            // Настраиваем HttpClient с увеличенными таймаутами для 4K генерации
            HttpClient httpClient = HttpClient.create()
                    .responseTimeout(Duration.ofMinutes(12)) // 12 минут для ответа
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000); // 30 секунд на подключение

            webClient = webClientBuilder
                    .baseUrl(baseUrl)
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .build();
        }
        return webClient;
    }

    @Override
    public Mono<ImageRs> generateImage(ImageRq request, Long userId) {
        log.debug("LaoZhangProvider: генерация изображения для пользователя {}", userId);

        Integer numImages = request.getNumImages();
        GenerationModelType modelType = request.getModel();
        Resolution resolution = request.getResolution();
        Integer pointsNeeded = generationProperties.getPointsNeeded(modelType, resolution, numImages);

        return userPointsService.hasEnoughPoints(userId, pointsNeeded)
                .flatMap(hasEnough -> {
                    if (!hasEnough) {
                        return Mono.error(new FalAIException(
                                "Недостаточно поинтов для генерации изображений. Требуется: " + pointsNeeded,
                                HttpStatus.PAYMENT_REQUIRED));
                    } else {
                        return sessionService.getOrCreateSession(request.getSessionId(), userId)
                                .flatMap(session -> {
                                    Long styleId = request.getStyleId();
                                    return artStyleService.getStyleById(styleId)
                                            .flatMap(style -> userService.findByIdOrThrow(userId)
                                                    .flatMap(user -> {
                                                        String username = user.getUsername();
                                                        String userPrompt = request.getPrompt();
                                                        String finalPrompt = userPrompt + ", " + style.getPrompt();
                                                        List<String> inputImageUrls = request.getInputImageUrls();

                                                        return userPointsService.deductPointsFromUser(userId, pointsNeeded)
                                                                .flatMap(userPoints -> {
                                                                    Integer balance = userPoints.getPoints();

                                                                    // Получаем имя модели для формирования endpoint
                                                                    String modelName = laoZhangMapper.getLaoZhangModelName(modelType);
                                                                    String endpoint = String.format(ENDPOINT_TEMPLATE, modelName);
                                                                    
                                                                    return laoZhangMapper.createRequest(
                                                                            request, finalPrompt, numImages, inputImageUrls, resolution)
                                                                            .flatMap(laoZhangRequest -> {
                                                                                boolean isNewImage = CollectionUtils.isEmpty(inputImageUrls);
                                                                                String operationType = isNewImage ? "создание" : "редактирование";
                                                                                log.info("Будет отправлен запрос в LaoZhang AI на {} изображений (операция: {}), endpoint: {}",
                                                                                        numImages, operationType, endpoint);

                                                                                Mono<LaoZhangResponseDTO> apiRequest = getWebClient().post()
                                                                                        .uri(endpoint)
                                                                                        .bodyValue(laoZhangRequest)
                                                                                        .retrieve()
                                                                                        .bodyToMono(new ParameterizedTypeReference<LaoZhangResponseDTO>() {})
                                                                                        .timeout(Duration.ofMinutes(12)); // 12 минут для генерации 4K

                                                                                // Обрабатываем отмену запроса (например, при разрыве соединения с клиентом)
                                                                                return apiRequest
                                                                                        .doOnCancel(() -> {
                                                                                            log.warn("Запрос к LaoZhang AI отменен для userId={}, возвращаем поинты: {}", userId, pointsNeeded);
                                                                                            userPointsService.addPointsToUser(userId, pointsNeeded)
                                                                                                    .subscribe(
                                                                                                            updated -> log.info("Поинты возвращены пользователю {} после отмены запроса: {}", userId, pointsNeeded),
                                                                                                            error -> log.error("Ошибка при возврате поинтов пользователю {}: {}", userId, error.getMessage())
                                                                                                    );
                                                                                        })
                                                                                        .flatMap(response -> extractBase64Images(response))
                                                                                        .flatMap(base64Images -> {
                                                                                            // Сохраняем все изображения в поддиректорию lz
                                                                                            return Flux.fromIterable(base64Images)
                                                                                                    .flatMap(base64Image -> base64ImageService.saveBase64ImageAndGetUrl(base64Image, username, "lz"))
                                                                                                    .collectList();
                                                                                        })
                                                                                        .map(imageUrls -> new ImageRs(imageUrls, balance))
                                                                                        .flatMap(response -> imageGenerationHistoryService.saveHistories(
                                                                                                userId, response.getImageUrls(), userPrompt,
                                                                                                session.getId(), inputImageUrls, styleId, request.getAspectRatio(),
                                                                                                modelType, resolution, GenerationProvider.LAOZHANG_AI)
                                                                                                .then(Mono.just(response)))
                                                                                        .flatMap(response -> sessionService.updateSessionTimestamp(session.getId())
                                                                                                .then(Mono.just(response)))
                                                                                        .doOnSuccess(response -> log.info("Успешно получен ответ с изображением для сессии {}: {}",
                                                                                                session.getId(), response))
                                                                                        .onErrorResume(e -> exceptionHandling(userId, e, pointsNeeded));
                                                                            });
                                                                });
                                                    }));
                                    });
                    }
                });
    }

    /**
     * Извлечь base64 изображения из ответа LaoZhang API (формат Gemini API).
     *
     * @param response ответ от LaoZhang API
     * @return список base64 строк в формате data:image/...;base64,...
     */
    private Mono<List<String>> extractBase64Images(LaoZhangResponseDTO response) {
        log.debug("Извлечение base64 изображений из ответа LaoZhang API");

        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
            return Mono.error(new FalAIException("Пустой ответ от LaoZhang API", HttpStatus.UNPROCESSABLE_ENTITY));
        }

        List<String> base64Images = new ArrayList<>();

        for (LaoZhangResponseDTO.Candidate candidate : response.getCandidates()) {
            if (candidate.getContent() != null && candidate.getContent().getParts() != null) {
                for (LaoZhangResponseDTO.Part part : candidate.getContent().getParts()) {
                    if (part.getInlineData() != null) {
                        // Формируем data URL из inlineData
                        String mimeType = part.getInlineData().getMimeType();
                        String base64Data = part.getInlineData().getData();
                        // Добавляем префикс data: для совместимости с Base64ImageService
                        String dataUrl = "data:" + mimeType + ";base64," + base64Data;
                        base64Images.add(dataUrl);
                    }
                }
            }
        }

        if (base64Images.isEmpty()) {
            log.warn("Не найдено base64 изображений в ответе LaoZhang API");
            return Mono.error(new FalAIException("Не найдено изображений в ответе от LaoZhang API", 
                    HttpStatus.UNPROCESSABLE_ENTITY));
        }

        log.info("Извлечено {} base64 изображений из ответа LaoZhang API", base64Images.size());
        return Mono.just(base64Images);
    }

    /**
     * Обработка исключений.
     */
    private Mono<ImageRs> exceptionHandling(Long userId, Throwable e, Integer pointsNeeded) {
        log.error("Ошибка при работе с LaoZhang AI для userId={}, pointsNeeded={}: {}", 
                userId, pointsNeeded, e.getMessage(), e);

        if (e instanceof FalAIException) {
            // Уже наш кастомный эксепшн — возврат поинтов уже был
            return Mono.error(e);
        } else if (e instanceof TimeoutException ||
                (e.getCause() != null && e.getCause() instanceof TimeoutException) ||
                (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout"))) {
            log.warn("Timeout при запросе к LaoZhang AI для userId={}. Возвращаем поинты.", userId);
            return userPointsService.addPointsToUser(userId, pointsNeeded)
                    .then(Mono.error(new FalAIException(
                            "Превышено время ожидания ответа от сервиса генерации. Попробуйте позже.",
                            HttpStatus.REQUEST_TIMEOUT)));
        } else if (e instanceof WebClientRequestException) {
            log.warn("Ошибка подключения к LaoZhang AI для userId={}. Возвращаем поинты.", userId);
            return userPointsService.addPointsToUser(userId, pointsNeeded)
                    .then(Mono.error(new FalAIException(
                            "Не удалось подключиться к сервису генерации. Проверьте интернет или попробуйте позже.",
                            HttpStatus.SERVICE_UNAVAILABLE)));
        } else if (e instanceof WebClientResponseException webE) {
            String responseBody = webE.getResponseBodyAsString();
            log.warn("Ошибка от LaoZhang AI для userId={}. Статус: {}, тело: {}. Возвращаем поинты.",
                    userId, webE.getStatusCode(), responseBody);
            String message = "Сервис генерации вернул ошибку. Статус: " + webE.getStatusCode()
                    + ", причина: " + webE.getStatusText();
            if (responseBody != null && !responseBody.isEmpty()) {
                message += ", тело ответа: " + responseBody;
            }
            return userPointsService.addPointsToUser(userId, pointsNeeded)
                    .then(Mono.error(new FalAIException(message, HttpStatus.UNPROCESSABLE_ENTITY)));
        } else {
            log.warn("Неизвестная ошибка при работе с LaoZhang AI для userId={}. Возвращаем поинты.", userId);
            return userPointsService.addPointsToUser(userId, pointsNeeded)
                    .then(Mono.error(new FalAIException(
                            "Произошла ошибка при работе с сервисом генерации: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR)));
        }
    }

    @Override
    public GenerationProvider getProviderName() {
        return GenerationProvider.LAOZHANG_AI;
    }

    @Override
    public Mono<Boolean> isAvailable() {
        // Простая проверка доступности - можно добавить health check если нужно
        String apiKey = laoZhangProperties.getApi().getKey();
        return Mono.just(apiKey != null && !apiKey.isEmpty());
    }

    @Override
    public Integer getPricePerImage(GenerationModelType modelType, Resolution resolution) {
        return generationProperties.getPointsNeeded(modelType, resolution, 1);
    }
}
