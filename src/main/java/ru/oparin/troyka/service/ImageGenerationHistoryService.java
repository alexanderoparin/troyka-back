package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.model.enums.QueueStatus;
import ru.oparin.troyka.model.enums.Resolution;
import ru.oparin.troyka.repository.ImageGenerationHistoryRepository;
import ru.oparin.troyka.util.JsonUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для работы с историей генерации изображений.
 * Предоставляет методы для сохранения и получения истории генераций с поддержкой сессий.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImageGenerationHistoryService {

    private final ImageGenerationHistoryRepository imageGenerationHistoryRepository;
    private final GenerationProperties generationProperties;

    /**
     * Сохранить историю генерации изображений для конкретного пользователя.
     * Перегрузка метода для случаев, когда userId известен напрямую (например, Telegram бот).
     *
     * @param userId         ID пользователя
     * @param imageUrls      список URL сгенерированных изображений
     * @param prompt         промпт пользователя
     * @param sessionId      идентификатор сессии
     * @param inputImageUrls список URL входных изображений (для отображения в истории)
     * @param styleId        идентификатор стиля (по умолчанию 1 - Без стиля)
     * @param aspectRatio    соотношение сторон
     * @param modelType      тип модели (сохраняется только для новых моделей)
     * @param resolution     разрешение (сохраняется только для новых моделей)
     * @param provider       провайдер генерации (FAL_AI или LAOZHANG_AI)
     * @return сохраненные записи истории
     */
    public Flux<ImageGenerationHistory> saveHistories(Long userId, Iterable<String> imageUrls, String prompt, Long sessionId,
                                                      List<String> inputImageUrls, Long styleId, String aspectRatio,
                                                      GenerationModelType modelType, Resolution resolution, GenerationProvider provider) {
        List<String> imageUrlsList = new ArrayList<>();
        imageUrls.forEach(imageUrlsList::add);
        String imageUrlsJson = JsonUtils.convertListToJson(imageUrlsList);
        String inputImageUrlsJson = JsonUtils.convertListToJson(inputImageUrls);
        
        String modelTypeToSave = modelType.getName();
        String resolutionToSave = (modelType.supportsResolution() && resolution != null)
                ? resolution.getValue() : null;

        // Рассчитываем стоимость генерации
        Integer numImages = imageUrlsList.size();
        Integer pointsCost = generationProperties.getPointsNeeded(modelType, resolution, numImages);
        BigDecimal costUsd = generationProperties.getCostUsd(modelType, resolution, numImages, provider);

        return imageGenerationHistoryRepository.saveWithJsonb(
                        userId,
                        imageUrlsJson,
                        prompt,
                        LocalDateTime.now(),
                        sessionId,
                        inputImageUrlsJson,
                        styleId,
                        aspectRatio != null ? aspectRatio : "1:1",
                        modelTypeToSave,
                        resolutionToSave,
                        numImages,
                        pointsCost,
                        costUsd,
                        provider.getCode()
                )
                .doOnNext(history -> log.info("Запись истории сохранена: {}", history))
                .flux();
    }

    /**
     * Получить последнюю запись истории для сессии и пользователя.
     *
     * @param sessionId идентификатор сессии
     * @param userId    идентификатор пользователя
     * @return последняя запись истории или пустой результат
     */
    public Mono<ImageGenerationHistory> getLastHistoryBySessionId(Long sessionId, Long userId) {
        log.debug("Получение последней записи истории для сессии {} и пользователя {}", sessionId, userId);
        return imageGenerationHistoryRepository.findByUserIdAndSessionIdAndDeletedFalseOrderByCreatedAtDesc(userId, sessionId)
                .next() // Берем только первую (последнюю) запись
                .doOnError(error -> log.error("Ошибка при получении последней записи истории для сессии {} и пользователя {}", sessionId, userId, error));
    }

    /**
     * Получить URL последнего сгенерированного изображения из конкретной сессии.
     *
     * @param userId    идентификатор пользователя
     * @param sessionId идентификатор сессии
     * @return URL последнего изображения из сессии или пустой результат
     */
    public Mono<String> getLastGeneratedImageUrlFromSession(Long userId, Long sessionId) {
        log.info("Получение URL последнего сгенерированного изображения для пользователя {} из сессии {}", userId, sessionId);

        return imageGenerationHistoryRepository.findByUserIdAndSessionIdAndDeletedFalseOrderByCreatedAtDesc(userId, sessionId)
                .next() // Берем только первую (последнюю) запись из сессии
                .flatMap(history -> {
                    List<String> imageUrls = JsonUtils.parseJsonToList(history.getImageUrls());
                    if (imageUrls != null && !imageUrls.isEmpty()) {
                        String lastImageUrl = imageUrls.get(imageUrls.size() - 1); // Берем последнее изображение
                        return Mono.just(lastImageUrl);
                    } else {
                        log.warn("Нет изображений в последней записи истории для пользователя {} из сессии {}", userId, sessionId);
                        return Mono.empty();
                    }
                })
                .doOnError(error -> log.error("Ошибка при получении URL последнего изображения для пользователя {} из сессии {}", userId, sessionId, error));
    }

    /**
     * Сохранить новую запись истории для очереди с полями очереди и JSONB.
     * Используется для создания записи при отправке запроса в очередь.
     *
     * @param userId         идентификатор пользователя
     * @param prompt         промпт
     * @param sessionId      идентификатор сессии
     * @param inputImageUrls список URL входных изображений (может быть null или пустым)
     * @param styleId        идентификатор стиля
     * @param aspectRatio    соотношение сторон
     * @param modelType      тип модели (сохраняется только для новых моделей)
     * @param resolution     разрешение (сохраняется только для новых моделей)
     * @param falRequestId   идентификатор запроса Fal.ai
     * @param queueStatus    статус очереди
     * @param numImages      количество запрошенных изображений
     * @return сохраненная запись
     */
    public Mono<ImageGenerationHistory> saveQueueRequest(Long userId, String prompt, Long sessionId,
                                                         List<String> inputImageUrls, Long styleId,
                                                         String aspectRatio, GenerationModelType modelType, Resolution resolution,
                                                         String falRequestId, QueueStatus queueStatus, Integer numImages) {
        String imageUrlsJson = JsonUtils.convertListToJson(List.of()); // Пустой массив для imageUrls
        String inputImageUrlsJson = inputImageUrls != null && !inputImageUrls.isEmpty()
                ? JsonUtils.convertListToJson(inputImageUrls)
                : null;
        LocalDateTime now = LocalDateTime.now();
        
        String modelTypeToSave = modelType.getName();
        String resolutionToSave = (modelType.supportsResolution() && resolution != null)
                ? resolution.getValue() : null;

        // Рассчитываем стоимость генерации
        Integer numImagesForCalculation = numImages != null ? numImages : 1;
        Integer pointsCost = generationProperties.getPointsNeeded(modelType, resolution, numImagesForCalculation);
        BigDecimal costUsd = generationProperties.getCostUsd(modelType, resolution, numImagesForCalculation);

        return imageGenerationHistoryRepository.saveQueueRequest(
                userId,
                imageUrlsJson,
                prompt,
                now,
                sessionId,
                inputImageUrlsJson,
                styleId,
                aspectRatio != null ? aspectRatio : "1:1",
                modelTypeToSave,
                resolutionToSave,
                falRequestId,
                queueStatus.name(),
                null,
                numImages,
                pointsCost,
                costUsd,
                now,
                GenerationProvider.FAL_AI.getCode() // Очередь используется только для FAL AI
        ).doOnSuccess(h -> log.info("Создана запись истории со статусом {}: id={}, falRequestId={}, numImages={}, pointsCost={}, costUsd=${}",
                queueStatus, h.getId(), falRequestId, numImages, pointsCost, costUsd));
    }

    /**
     * Обновить статус запроса в очереди.
     *
     * @param id             идентификатор записи
     * @param imageUrls      список URL сгенерированных изображений
     * @param inputImageUrls список URL входных изображений (может быть null)
     * @param queueStatus    статус очереди
     * @param queuePosition  позиция в очереди
     * @return обновленная запись
     */
    public Mono<ImageGenerationHistory> updateQueueStatus(Long id, List<String> imageUrls, List<String> inputImageUrls,
                                                          QueueStatus queueStatus, Integer queuePosition) {
        String imageUrlsJsonStr = JsonUtils.convertListToJson(imageUrls != null ? imageUrls : List.of());
        String inputImageUrlsJson = inputImageUrls != null && !inputImageUrls.isEmpty()
                ? JsonUtils.convertListToJson(inputImageUrls)
                : null;

        return imageGenerationHistoryRepository.updateWithJsonb(
                id,
                imageUrlsJsonStr,
                inputImageUrlsJson,
                queueStatus.name(),
                queuePosition,
                LocalDateTime.now()
        ).doOnSuccess(imageGenerationHistory -> {
            if (QueueStatus.COMPLETED.equals(queueStatus)) {
                log.info("Создана запись истории генерации изображений: {}", imageGenerationHistory);
            }
        });
    }

    /**
     * Найти все активные записи истории пользователя (в очереди или обрабатываются).
     *
     * @param userId        идентификатор пользователя
     * @param queueStatuses список статусов для поиска
     * @return поток активных записей истории пользователя
     */
    public Flux<ImageGenerationHistory> findByUserIdAndQueueStatusIn(Long userId, List<String> queueStatuses) {
        return imageGenerationHistoryRepository.findByUserIdAndQueueStatusIn(userId, queueStatuses);
    }

    /**
     * Получить запись истории по ID.
     *
     * @param id идентификатор записи
     * @return запись истории или пустой результат
     */
    public Mono<ImageGenerationHistory> findById(Long id) {
        return imageGenerationHistoryRepository.findById(id);
    }

}