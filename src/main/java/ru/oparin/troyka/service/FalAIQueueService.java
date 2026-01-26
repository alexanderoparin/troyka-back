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
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.QueueStatus;
import ru.oparin.troyka.model.enums.Resolution;

import java.time.Duration;
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
    private final AdminNotificationService adminNotificationService;

    public FalAIQueueService(WebClient.Builder webClientBuilder,
                             FalAiProperties falAiProperties,
                             GenerationProperties generationProperties,
                             ImageGenerationHistoryService imageGenerationHistoryService,
                             UserPointsService userPointsService,
                             SessionService sessionService,
                             ArtStyleService artStyleService,
                             FalAIQueueMapper mapper,
                             ObjectMapper objectMapper,
                             AdminNotificationService adminNotificationService) {
        this.falAiProperties = falAiProperties;
        this.generationProperties = generationProperties;
        this.imageGenerationHistoryService = imageGenerationHistoryService;
        this.userPointsService = userPointsService;
        this.sessionService = sessionService;
        this.artStyleService = artStyleService;
        this.adminNotificationService = adminNotificationService;
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
        GenerationModelType modelType = imageRq.getModel();
        Resolution resolution = imageRq.getResolution();
        Integer pointsNeeded = generationProperties.getPointsNeeded(modelType, resolution, numImages);

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

                                            FalAIRequestDTO requestBody = mapper.createRqBody(imageRq, finalPrompt, numImages, inputImageUrls, resolution);

                                            boolean isNewImage = CollectionUtils.isEmpty(inputImageUrls);
                                            String modelEndpoint = modelType.getEndpoint(isNewImage);
                                            String fullModelPath = PREFIX_PATH + modelEndpoint;

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
                                                                modelType, resolution,
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
        
        // Используем модель из истории (дефолт установлен в сущности)
        GenerationModelType modelType = history.getGenerationModelType();
        String model = modelType.getEndpoint(isNewImage);
        
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
                            .flatMap(rawResponse -> {
                                try {
                                    FalAIQueueStatusDTO status = objectMapper.readValue(rawResponse, FalAIQueueStatusDTO.class);
                                    return Mono.just(status);
                                } catch (Exception e) {
                                    log.error("Ошибка при парсинге ответа от Fal.ai для запроса {}. Сырой ответ: {}", 
                                            falRequestId, rawResponse, e);
                                    return Mono.error(new FalAIException("Не удалось разобрать ответ от Fal.ai", HttpStatus.INTERNAL_SERVER_ERROR));
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
                            status.getQueuePosition()
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
                .bodyToMono(new ParameterizedTypeReference<FalAIResponseDTO>() {})
                .flatMap(response -> processSuccessfulResponse(history, response))
                .onErrorResume(e -> handleRequestError(e, history, resultPath));
    }

    /**
     * Обработать завершенный запрос: получить результат и обновить запись в БД.
     *
     * @param history запись истории генерации
     * @param responseUrl URL для получения результата от Fal.ai
     * @return обновленная запись истории с заполненными imageUrls
     */
    public Mono<ImageGenerationHistory> processCompletedRequest(ImageGenerationHistory history, String responseUrl) {
        return queueWebClient.get()
                .uri(responseUrl)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<FalAIResponseDTO>() {})
                .flatMap(response -> processSuccessfulResponse(history, response))
                .onErrorResume(e -> handleRequestError(e, history, responseUrl));
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
                                                                      String aspectRatio, GenerationModelType modelType, Resolution resolution,
                                                                      String falRequestId, Integer numImages) {
        return userPointsService.deductPointsFromUser(userId, pointsNeeded)
                .flatMap(userPoints -> {
                    return imageGenerationHistoryService.saveQueueRequest(
                            userId,
                            prompt,
                            sessionId,
                            inputImageUrls,
                            styleId,
                            aspectRatio != null ? aspectRatio : "1:1",
                            modelType,
                            resolution,
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

            checkExhaustedBalance(webE, responseBody);
            return Mono.error(new FalAIException(
                    "Ошибка при отправке запроса в очередь: " + webE.getStatusCode(),
                    HttpStatus.UNPROCESSABLE_ENTITY));
        } else {
            log.warn("Ошибка подключения к Fal.ai при отправке в очередь для userId={}", userId);
            return Mono.error(new FalAIException(
                    "Не удалось подключиться к сервису генерации. Попробуйте позже.",
                    HttpStatus.SERVICE_UNAVAILABLE));
        }
    }

    /**
     * Проверяем, является ли это ошибкой исчерпанного баланса.
     * Отправляет уведомление администраторам, если обнаружена проблема с балансом.
     */
    private void checkExhaustedBalance(WebClientResponseException webE, String responseBody) {
        if (webE.getStatusCode().value() == 403) {
            String lowerBody = responseBody != null ? responseBody.toLowerCase() : "";
            // Проверяем различные варианты сообщений об исчерпанном балансе
            if (lowerBody.contains("exhausted balance") || 
                lowerBody.contains("user is locked") ||
                lowerBody.contains("locked") && lowerBody.contains("balance") ||
                lowerBody.contains("top up your balance")) {
                log.error("Обнаружена критическая ошибка: исчерпан баланс Fal.ai. Отправляем уведомление администраторам.");
                // Отправляем уведомление админам (не блокируем основной поток)
                adminNotificationService.notifyAdminsAboutFalBalance(responseBody)
                        .subscribe(
                                null,
                                error -> log.error("Ошибка при отправке уведомления администраторам о балансе Fal.ai", error)
                        );
            }
        }
    }

    /**
     * Получить сообщение об ошибке политики контента.
     */
    private String getContentPolicyViolationMessage() {
        return "Запрос был отклонен системой безопасности. Возможно, контент нарушает политику использования.";
    }

    /**
     * Обработать успешный ответ от Fal.ai: сохранить изображения и обновить запись в БД.
     */
    private Mono<ImageGenerationHistory> processSuccessfulResponse(ImageGenerationHistory history, FalAIResponseDTO response) {
        List<String> imageUrls = response.getImages().stream()
                .map(FalAIImageDTO::getUrl)
                .toList();

        history.setImageUrls(imageUrls);
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
                        null
                )
                .flatMap(updatedHistory ->
                        sessionService.updateSessionTimestamp(history.getSessionId())
                                .then(Mono.just(updatedHistory)));
    }

    /**
     * Обработать ошибку при получении результата запроса.
     */
    private Mono<ImageGenerationHistory> handleRequestError(Throwable e, ImageGenerationHistory history, String requestPath) {
        // Проверяем, является ли это ошибкой 422 (UnprocessableEntity)
        if (e instanceof WebClientResponseException webE && webE.getStatusCode().value() == 422) {
            String errorMessage = getContentPolicyViolationMessage();
            log.warn("Запрос {} отклонен Fal.ai (422): {}. Тело ответа: {}", 
                    history.getFalRequestId(), errorMessage, webE.getResponseBodyAsString());
            return handleFailedRequestWithMessage(history, errorMessage);
        }
        log.error("Ошибка при получении результата для запроса {} из {}", history.getFalRequestId(), requestPath, e);
        return handleFailedRequest(history);
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
        GenerationModelType modelType = history.getGenerationModelType();
        Resolution resolution = history.getResolutionEnum();
        Integer pointsNeeded = generationProperties.getPointsNeeded(modelType, resolution, history.getNumImages());

        return imageGenerationHistoryService.updateQueueStatus(
                        history.getId(),
                        history.getImageUrls(),
                        history.getInputImageUrls(),
                        QueueStatus.FAILED,
                        null
                )
                .flatMap(updatedHistory -> {
                    log.warn("Запрос {} завершился с ошибкой: {}. Возвращаем {} поинтов пользователю {}",
                            history.getFalRequestId(), errorMessage, pointsNeeded, history.getUserId());
                    return userPointsService.addPointsToUser(history.getUserId(), pointsNeeded)
                            .then(Mono.just(updatedHistory));
                });
    }

    /**
     * Синхронная генерация изображения через очередь с ожиданием результата.
     * Используется для fallback, когда нужен синхронный результат.
     *
     * @param imageRq запрос на генерацию изображения
     * @param userId  идентификатор пользователя
     * @return ответ с сгенерированными изображениями
     */
    public Mono<ImageRs> generateImageSync(ImageRq imageRq, Long userId) {
        log.info("Синхронная генерация через очередь FAL AI для userId={}", userId);
        
        // Отправляем запрос в очередь
        return submitToQueue(imageRq, userId)
                .flatMap(statusDTO -> {
                    if (statusDTO.getId() == null) {
                        return Mono.error(new FalAIException(
                                "Не удалось создать запрос в очереди", HttpStatus.INTERNAL_SERVER_ERROR));
                    }
                    
                    // Получаем запись истории для опроса статуса
                    return imageGenerationHistoryService.findById(statusDTO.getId())
                            .switchIfEmpty(Mono.error(new FalAIException(
                                    "Запись истории не найдена", HttpStatus.INTERNAL_SERVER_ERROR)))
                            .flatMap(history -> {
                                // Если уже завершено (не должно происходить, но на всякий случай)
                                if (history.getQueueStatus() == QueueStatus.COMPLETED && 
                                    history.getImageUrls() != null && !history.getImageUrls().isEmpty()) {
                                    return userPointsService.getUserPoints(userId)
                                            .map(balance -> new ImageRs(history.getImageUrls(), balance));
                                }
                                
                                // Если уже провалилось
                                if (history.getQueueStatus() == QueueStatus.FAILED) {
                                    return Mono.error(new FalAIException(
                                            "Запрос завершился с ошибкой", HttpStatus.INTERNAL_SERVER_ERROR));
                                }
                                
                                // Ожидаем завершения, опрашивая статус
                                long pollingInterval = falAiProperties.getQueue().getPollingIntervalMs();
                                long maxWaitTime = falAiProperties.getQueue().getMaxWaitTimeMs();
                                
                                return pollStatusUntilComplete(history, pollingInterval, maxWaitTime)
                                        .flatMap(completedHistory -> {
                                            if (completedHistory.getQueueStatus() == QueueStatus.COMPLETED) {
                                                if (completedHistory.getImageUrls() == null || 
                                                    completedHistory.getImageUrls().isEmpty()) {
                                                    return Mono.error(new FalAIException(
                                                            "Запрос завершен, но изображения не получены", 
                                                            HttpStatus.INTERNAL_SERVER_ERROR));
                                                }
                                                return userPointsService.getUserPoints(userId)
                                                        .map(balance -> new ImageRs(
                                                                completedHistory.getImageUrls(), 
                                                                balance));
                                            } else {
                                                // FAILED
                                                return Mono.error(new FalAIException(
                                                        "Запрос завершился с ошибкой", 
                                                        HttpStatus.INTERNAL_SERVER_ERROR));
                                            }
                                        });
                            });
                });
    }

    /**
     * Опросить статус запроса до завершения (COMPLETED или FAILED).
     *
     * @param history         запись истории генерации
     * @param pollingInterval интервал опроса в миллисекундах
     * @param maxWaitTime     максимальное время ожидания в миллисекундах
     * @return завершенная запись истории
     */
    private Mono<ImageGenerationHistory> pollStatusUntilComplete(
            ImageGenerationHistory history, 
            long pollingInterval, 
            long maxWaitTime) {
        
        long startTime = System.currentTimeMillis();
        
        return Mono.defer(() -> pollStatus(history))
                .flatMap(updatedHistory -> {
                    QueueStatus status = updatedHistory.getQueueStatus();
                    
                    // Если завершено (успешно или с ошибкой), возвращаем
                    if (status == QueueStatus.COMPLETED || status == QueueStatus.FAILED) {
                        return Mono.just(updatedHistory);
                    }
                    
                    // Проверяем таймаут
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed >= maxWaitTime) {
                        log.warn("Превышено максимальное время ожидания для запроса {}: {} мс", 
                                history.getFalRequestId(), elapsed);
                        return Mono.error(new FalAIException(
                                "Превышено время ожидания ответа от сервиса генерации. Попробуйте позже.",
                                HttpStatus.REQUEST_TIMEOUT));
                    }
                    
                    // Ждем интервал и опрашиваем снова
                    return Mono.delay(Duration.ofMillis(pollingInterval))
                            .then(pollStatusUntilComplete(updatedHistory, pollingInterval, maxWaitTime));
                })
                .onErrorResume(e -> {
                    // Если ошибка при опросе, пробуем еще раз после задержки
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed >= maxWaitTime) {
                        return Mono.error(new FalAIException(
                                "Превышено время ожидания ответа от сервиса генерации. Попробуйте позже.",
                                HttpStatus.REQUEST_TIMEOUT));
                    }
                    log.warn("Ошибка при опросе статуса запроса {}, повторная попытка через {} мс: {}", 
                            history.getFalRequestId(), pollingInterval, e.getMessage());
                    return Mono.delay(Duration.ofMillis(pollingInterval))
                            .then(pollStatusUntilComplete(history, pollingInterval, maxWaitTime));
                });
    }
}

