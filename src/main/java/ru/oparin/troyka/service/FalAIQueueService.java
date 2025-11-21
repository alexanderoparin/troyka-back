package ru.oparin.troyka.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.config.properties.FalAiProperties;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.exception.FalAIException;
import ru.oparin.troyka.mapper.FalAIQueueMapper;
import ru.oparin.troyka.model.dto.fal.*;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;
import ru.oparin.troyka.model.enums.QueueStatus;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Сервис для работы с очередями Fal.ai API.
 * Предоставляет методы для отправки запросов в очередь и отслеживания их статуса.
 */
@Slf4j
@Service
public class FalAIQueueService {

    private static final String QUEUE_BASE_URL = "https://queue.fal.run";
    private static final String PREFIX_PATH = "/fal-ai/";
    private static final List<QueueStatus> ACTIVE_STATUSES = Arrays.asList(QueueStatus.IN_QUEUE, QueueStatus.IN_PROGRESS);

    private final WebClient queueWebClient;
    private final FalAiProperties falAiProperties;
    private final GenerationProperties generationProperties;
    private final ImageGenerationHistoryService imageGenerationHistoryService;
    private final UserPointsService userPointsService;
    private final SessionService sessionService;
    private final ArtStyleService artStyleService;
    private final FalAIQueueMapper mapper;
    private final ObjectMapper objectMapper;

    public FalAIQueueService(WebClient.Builder webClientBuilder,
                             FalAiProperties falAiProperties,
                             GenerationProperties generationProperties,
                             ImageGenerationHistoryService imageGenerationHistoryService,
                             UserPointsService userPointsService,
                             SessionService sessionService,
                             ArtStyleService artStyleService, FalAIQueueMapper mapper,
                             ObjectMapper objectMapper) {
        this.falAiProperties = falAiProperties;
        this.generationProperties = generationProperties;
        this.imageGenerationHistoryService = imageGenerationHistoryService;
        this.userPointsService = userPointsService;
        this.sessionService = sessionService;
        this.artStyleService = artStyleService;
        this.mapper = mapper;
        this.objectMapper = objectMapper;

        this.queueWebClient = webClientBuilder
                .baseUrl(QUEUE_BASE_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Key " + falAiProperties.getApi().getKey())
                .build();
    }

    /**
     * Отправить запрос в очередь Fal.ai и создать запись в БД.
     *
     * @param imageRq запрос на генерацию изображения
     * @param userId идентификатор пользователя
     * @return запись в ImageGenerationHistory с falRequestId
     */
    public Mono<FalAIQueueRequestStatusDTO> submitToQueue(ImageRq imageRq, Long userId) {
        Integer numImages = imageRq.getNumImages();
        Integer pointsNeeded = numImages * generationProperties.getPointsPerImage();

        return userPointsService.hasEnoughPoints(userId, pointsNeeded)
                .flatMap(hasEnough -> {
                    if (!hasEnough) {
                        return Mono.error(new FalAIException(
                                "Недостаточно поинтов для генерации изображений. Требуется: " + pointsNeeded,
                                HttpStatus.PAYMENT_REQUIRED));
                    }

                    return sessionService.getOrCreateSession(imageRq.getSessionId(), userId)
                            .flatMap(session -> {
                                Long styleId = imageRq.getStyleId();
                                return artStyleService.getStyleById(styleId)
                                        .flatMap(style -> {
                                            String userPrompt = imageRq.getPrompt();
                                            String finalPrompt = userPrompt + ", " + style.getPrompt();
                                            List<String> inputImageUrls = imageRq.getInputImageUrls();

                                            FalAIRequestDTO requestBody = mapper.createRqBody(imageRq, finalPrompt, numImages, inputImageUrls);

                                            boolean isNewImage = CollectionUtils.isEmpty(inputImageUrls);
                                            String model = isNewImage ? falAiProperties.getModel().getCreate() : falAiProperties.getModel().getEdit();
                                            String fullModelPath = PREFIX_PATH + model;

                                            log.info("Отправка запроса в очередь Fal.ai на {} изображений по адресу '{}'",
                                                    numImages, QUEUE_BASE_URL + fullModelPath);

                                            return queueWebClient.post()
                                                    .uri(fullModelPath)
                                                    .bodyValue(requestBody)
                                                    .retrieve()
                                                    .bodyToMono(FalAIQueueResponseDTO.class)
                                                    .flatMap(queueResponse -> {
                                                        log.info("Получен ответ от очереди Fal.ai: requestId={}, gatewayRequestId={}",
                                                                queueResponse.getRequestId(), queueResponse.getGatewayRequestId());

                                                        return deductPointsAndCreateHistory(userId, pointsNeeded, session.getId(),
                                                                userPrompt, inputImageUrls, styleId, imageRq.getAspectRatio(),
                                                                queueResponse.getRequestId(), numImages);
                                                    })
                                                    .onErrorResume(e -> {
                                                        log.error("Ошибка при отправке запроса в очередь Fal.ai для userId={}", userId, e);
                                                        return handleQueueError(userId, e, pointsNeeded);
                                                    });
                                        });
                            });
                })
                .map(mapper::toStatusDTO);
    }

    /**
     * Опросить статус запроса в очереди Fal.ai и обновить запись в БД.
     *
     * @param history запись истории генерации
     * @return обновленная запись истории
     */
    public Mono<ImageGenerationHistory> pollStatus(ImageGenerationHistory history) {
        String falRequestId = history.getFalRequestId();
        boolean isNewImage = CollectionUtils.isEmpty(history.getInputImageUrls());
        String model = isNewImage ? falAiProperties.getModel().getCreate() : falAiProperties.getModel().getEdit();
        // Извлекаем базовый model_id (убираем подпуть после "/", если есть)
        String baseModelId = model.contains("/") ? model.substring(0, model.indexOf("/")) : model;
        String statusPath = PREFIX_PATH + baseModelId + "/requests/" + falRequestId + "/status";

        return queueWebClient.get()
                .uri(statusPath)
                .<FalAIQueueStatusDTO>exchangeToMono(clientResponse -> {
                    if (clientResponse.statusCode().isError()) {
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.warn("Ошибка при опросе статуса запроса {}: статус {}, тело: {}",
                                            falRequestId, clientResponse.statusCode(), errorBody);
                                    return Mono.error(new WebClientResponseException(
                                            clientResponse.statusCode().value(),
                                            "Fal.ai API status error",
                                            clientResponse.headers().asHttpHeaders(),
                                            errorBody.getBytes(),
                                            null
                                    ));
                                });
                    }
                    return clientResponse.bodyToMono(String.class)
                            .doOnNext(rawResponse -> {
                                log.info("Сырой ответ от Fal.ai для запроса {}: {}", falRequestId, rawResponse);
                            })
                            .flatMap(rawResponse -> {
                                try {
                                    FalAIQueueStatusDTO status = objectMapper.readValue(rawResponse, FalAIQueueStatusDTO.class);
                                    log.info("Распарсенный ответ от Fal.ai для запроса {}: status={}, responseUrl={}, queuePosition={}, полный объект: {}", 
                                            falRequestId, status.getStatus(), status.getResponseUrl(), status.getQueuePosition(), status);
                                    return Mono.just(status);
                                } catch (Exception e) {
                                    log.error("Ошибка при парсинге ответа от Fal.ai для запроса {}. Сырой ответ: {}", 
                                            falRequestId, rawResponse, e);
                                    return Mono.error(new RuntimeException("Failed to parse Fal.ai response", e));
                                }
                            });
                })
                .flatMap(status -> {
                    QueueStatus queueStatus = status.getStatus();
                    Integer queuePosition = status.getQueuePosition();
                    log.debug("Статус запроса {}: {}, позиция в очереди: {}", falRequestId, queueStatus, queuePosition);

                    // Если статус null, не обновляем запись
                    if (queueStatus == null) {
                        log.warn("Получен null статус для запроса {}, пропускаем обновление. Полный ответ: {}", falRequestId, status);
                        return Mono.just(history);
                    }

                    // Если статус COMPLETED, не сохраняем запись здесь, а сразу обрабатываем результат
                    // Это избегает двойного сохранения и проблем с null значениями
                    if (queueStatus == QueueStatus.COMPLETED) {
                        String responseUrl = status.getResponseUrl();
                        log.debug("COMPLETED статус для запроса {}: responseUrl={}, полный объект status={}", 
                                falRequestId, responseUrl, status);
                        if (responseUrl == null) {
                            log.warn("Получен COMPLETED статус без responseUrl для запроса {}, пробуем получить результат напрямую по request_id", 
                                    falRequestId);
                            // Пробуем получить результат напрямую по request_id (без /status в пути)
                            String resultPath = PREFIX_PATH + baseModelId + "/requests/" + falRequestId;
                            log.info("Попытка получить результат напрямую по пути: {}", resultPath);
                            return processCompletedRequestDirectly(history, resultPath);
                        }
                        // Обновляем статус перед обработкой результата
                        history.setQueueStatus(queueStatus);
                        history.setQueuePosition(status.getQueuePosition());
                        history.setUpdatedAt(LocalDateTime.now());
                        return processCompletedRequest(history, responseUrl);
                    } else if (queueStatus == QueueStatus.FAILED) {
                        return handleFailedRequest(history);
                    }

                    // Для других статусов (IN_QUEUE, IN_PROGRESS) обновляем запись через сервис
                    return imageGenerationHistoryService.updateQueueStatus(
                            history.getId(),
                            history.getImageUrls(),
                            history.getInputImageUrls(),
                            queueStatus,
                            status.getQueuePosition(),
                            history.getDescription()
                    );
                })
                .onErrorResume(e -> {
                    log.error("Ошибка при опросе статуса запроса {} в очереди Fal.ai", falRequestId, e);
                    return Mono.just(history);
                });
    }

    /**
     * Получить статус запроса из БД по внутреннему ID.
     *
     * @param id внутренний идентификатор записи в ImageGenerationHistory
     * @return запись истории или пустой результат
     */
    public Mono<FalAIQueueRequestStatusDTO> getRequestStatus(Long id, Long userId) {
        return imageGenerationHistoryService.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Запрос не найден")))
                .flatMap(history -> {
                    if (!history.getUserId().equals(userId)) {
                        return Mono.error(new IllegalArgumentException("Доступ запрещен"));
                    }
                    return Mono.just(history);
                })
                .map(mapper::toStatusDTO);
    }

    /**
     * Обработать завершенный запрос: получить результат напрямую по request_id (когда responseUrl отсутствует).
     *
     * @param history запись истории генерации
     * @param resultPath путь для получения результата (например, /fal-ai/{model}/requests/{request_id})
     * @return обновленная запись истории с заполненными imageUrls
     */
    private Mono<ImageGenerationHistory> processCompletedRequestDirectly(ImageGenerationHistory history, String resultPath) {
        log.info("Обработка завершенного запроса {}: получение результата напрямую по пути {}", history.getFalRequestId(), resultPath);

        return queueWebClient.get()
                .uri(resultPath)
                .retrieve()
                .onStatus(status -> status.value() == 422, clientResponse -> {
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                String errorMessage = "Запрос был отклонен системой безопасности. Возможно, контент нарушает политику использования.";
                                log.warn("Запрос {} отклонен Fal.ai (422): {}. Тело ответа: {}", 
                                        history.getFalRequestId(), errorMessage, errorBody);
                                // Возвращаем ошибку, которая будет обработана в onErrorResume
                                return Mono.error(new RuntimeException("CONTENT_POLICY_VIOLATION: " + errorMessage));
                            });
                })
                .bodyToMono(new ParameterizedTypeReference<FalAIResponseDTO>() {})
                .flatMap(response -> {
                    List<String> imageUrls = response.getImages().stream()
                            .map(FalAIImageDTO::getUrl)
                            .toList();

                    history.setImageUrls(imageUrls);
                    history.setDescription(response.getDescription());
                    history.setQueueStatus(QueueStatus.COMPLETED);
                    history.setQueuePosition(null);
                    history.setUpdatedAt(LocalDateTime.now());

                    log.info("Результат получен напрямую для запроса {}: {} изображений", history.getFalRequestId(), imageUrls.size());

                    // Обновляем запись через сервис
                    return imageGenerationHistoryService.updateQueueStatus(
                            history.getId(),
                            imageUrls,
                            history.getInputImageUrls(),
                            QueueStatus.COMPLETED,
                            null,
                            response.getDescription()
                    )
                            .flatMap(updatedHistory ->
                                    sessionService.updateSessionTimestamp(history.getSessionId())
                                    .then(Mono.just(updatedHistory)));
                })
                .onErrorResume(e -> {
                    if (e.getMessage() != null && e.getMessage().contains("CONTENT_POLICY_VIOLATION")) {
                        String errorMessage = e.getMessage().replace("CONTENT_POLICY_VIOLATION: ", "");
                        log.error("Ошибка при получении результата для запроса {}: {}", history.getFalRequestId(), errorMessage);
                        return handleFailedRequestWithMessage(history, errorMessage);
                    }
                    log.error("Ошибка при получении результата напрямую для запроса {} из {}", history.getFalRequestId(), resultPath, e);
                    return handleFailedRequest(history);
                });
    }

    /**
     * Обработать завершенный запрос: получить результат и обновить запись в БД.
     *
     * @param history запись истории генерации
     * @param responseUrl URL для получения результата от Fal.ai
     * @return обновленная запись истории с заполненными imageUrls
     */
    public Mono<ImageGenerationHistory> processCompletedRequest(ImageGenerationHistory history, String responseUrl) {
        log.info("Обработка завершенного запроса {}: получение результата из {}", history.getFalRequestId(), responseUrl);

        return queueWebClient.get()
                .uri(responseUrl)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<FalAIResponseDTO>() {})
                .flatMap(response -> {
                    List<String> imageUrls = response.getImages().stream()
                            .map(FalAIImageDTO::getUrl)
                            .toList();

                    history.setImageUrls(imageUrls);
                    history.setDescription(response.getDescription());
                    history.setQueueStatus(QueueStatus.COMPLETED);
                    history.setQueuePosition(null);
                    history.setUpdatedAt(LocalDateTime.now());

                    log.info("Результат получен для запроса {}: {} изображений", history.getFalRequestId(), imageUrls.size());

                    // Обновляем запись через сервис
                    return imageGenerationHistoryService.updateQueueStatus(
                            history.getId(),
                            imageUrls,
                            history.getInputImageUrls(),
                            QueueStatus.COMPLETED,
                            null,
                            response.getDescription()
                    )
                            .flatMap(updatedHistory ->
                                    sessionService.updateSessionTimestamp(history.getSessionId())
                                    .then(Mono.just(updatedHistory)));
                })
                .onErrorResume(e -> {
                    log.error("Ошибка при получении результата для запроса {} из {}", history.getFalRequestId(), responseUrl, e);
                    return handleFailedRequest(history);
                });
    }

    /**
     * Получить все активные запросы пользователя.
     *
     * @param userId идентификатор пользователя
     * @return поток активных записей истории
     */
    public Flux<FalAIQueueRequestStatusDTO> getUserActiveRequests(Long userId) {
        List<String> activeStatusStrings = ACTIVE_STATUSES.stream()
                .map(QueueStatus::name)
                .toList();
        return imageGenerationHistoryService.findByUserIdAndQueueStatusIn(userId, activeStatusStrings)
                .map(mapper::toStatusDTO);
    }

    /**
     * Списать поинты и создать запись истории со статусом IN_QUEUE.
     */
    private Mono<ImageGenerationHistory> deductPointsAndCreateHistory(Long userId, Integer pointsNeeded, Long sessionId,
                                                                      String prompt, List<String> inputImageUrls, Long styleId,
                                                                      String aspectRatio, String falRequestId, Integer numImages) {
        return userPointsService.deductPointsFromUser(userId, pointsNeeded)
                .flatMap(userPoints -> {
                    return imageGenerationHistoryService.saveQueueRequest(
                            userId,
                            prompt,
                            sessionId,
                            inputImageUrls,
                            styleId,
                            aspectRatio != null ? aspectRatio : "1:1",
                            falRequestId,
                            QueueStatus.IN_QUEUE,
                            numImages
                    );
                });
    }

    /**
     * Обработать ошибку при отправке запроса в очередь.
     */
    private Mono<ImageGenerationHistory> handleQueueError(Long userId, Throwable e, Integer pointsNeeded) {
        if (e instanceof WebClientResponseException webE) {
            String responseBody = webE.getResponseBodyAsString();
            log.warn("Ошибка от Fal.ai при отправке в очередь для userId={}. Статус: {}, тело: {}",
                    userId, webE.getStatusCode(), responseBody);
            return userPointsService.addPointsToUser(userId, pointsNeeded)
                    .then(Mono.error(new FalAIException(
                            "Ошибка при отправке запроса в очередь: " + webE.getStatusCode(),
                            HttpStatus.UNPROCESSABLE_ENTITY)));
        } else {
            log.warn("Ошибка подключения к Fal.ai при отправке в очередь для userId={}", userId);
            return userPointsService.addPointsToUser(userId, pointsNeeded)
                    .then(Mono.error(new FalAIException(
                            "Не удалось подключиться к сервису генерации. Попробуйте позже.",
                            HttpStatus.SERVICE_UNAVAILABLE)));
        }
    }

    /**
     * Обработать неудачный запрос: обновить статус и вернуть поинты.
     */
    private Mono<ImageGenerationHistory> handleFailedRequest(ImageGenerationHistory history) {
        return handleFailedRequestWithMessage(history, "Не удалось получить результат генерации");
    }

    /**
     * Обработать неудачный запрос с сообщением об ошибке: обновить статус и вернуть поинты.
     */
    private Mono<ImageGenerationHistory> handleFailedRequestWithMessage(ImageGenerationHistory history, String errorMessage) {
        Integer pointsNeeded = history.getNumImages() * generationProperties.getPointsPerImage();

        String description = errorMessage != null ? errorMessage : history.getDescription();

        return imageGenerationHistoryService.updateQueueStatus(
                        history.getId(),
                        history.getImageUrls(),
                        history.getInputImageUrls(),
                        QueueStatus.FAILED,
                        null,
                        description
                )
                .flatMap(updatedHistory -> {
                    log.warn("Запрос {} завершился с ошибкой: {}. Возвращаем {} поинтов пользователю {}",
                            history.getFalRequestId(), errorMessage, pointsNeeded, history.getUserId());
                    return userPointsService.addPointsToUser(history.getUserId(), pointsNeeded)
                            .then(Mono.just(updatedHistory));
                });
    }
}

