package ru.oparin.troyka.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository для работы с историей генерации изображений.
 * Предоставляет методы для CRUD операций и специфичные запросы для истории генераций.
 * Использует R2DBC для реактивного взаимодействия с базой данных.
 */
public interface ImageGenerationHistoryRepository extends ReactiveCrudRepository<ImageGenerationHistory, Long> {

    /**
     * Найти все записи истории пользователя, отсортированные по дате создания (новые первые).
     *
     * @param userId идентификатор пользователя
     * @return поток записей истории пользователя
     */
    Flux<ImageGenerationHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Найти все не удаленные записи истории пользователя, отсортированные по дате создания (новые первые).
     *
     * @param userId идентификатор пользователя
     * @return поток не удаленных записей истории пользователя
     */
    Flux<ImageGenerationHistory> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(Long userId);

    /**
     * Найти все записи истории пользователя из конкретной сессии, отсортированные по дате создания (новые первые).
     *
     * @param userId идентификатор пользователя
     * @param sessionId идентификатор сессии
     * @return поток записей истории пользователя из сессии
     */
    Flux<ImageGenerationHistory> findByUserIdAndSessionIdOrderByCreatedAtDesc(Long userId, Long sessionId);

    /**
     * Найти все не удаленные записи истории пользователя из конкретной сессии, отсортированные по дате создания (новые первые).
     *
     * @param userId идентификатор пользователя
     * @param sessionId идентификатор сессии
     * @return поток не удаленных записей истории пользователя из сессии
     */
    Flux<ImageGenerationHistory> findByUserIdAndSessionIdAndDeletedFalseOrderByCreatedAtDesc(Long userId, Long sessionId);

    /**
     * Найти все записи истории сессии, отсортированные по дате создания (по порядку диалога).
     *
     * @param sessionId идентификатор сессии
     * @param pageable параметры пагинации
     * @return поток записей истории сессии
     */
    Flux<ImageGenerationHistory> findBySessionIdOrderByCreatedAtAsc(Long sessionId, Pageable pageable);

    /**
     * Найти все записи истории сессии, отсортированные по дате создания (по порядку диалога).
     *
     * @param sessionId идентификатор сессии
     * @return поток всех записей истории сессии
     */
    Flux<ImageGenerationHistory> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    /**
     * Найти все не удаленные записи истории сессии, отсортированные по дате создания (по порядку диалога).
     *
     * @param sessionId идентификатор сессии
     * @return поток всех не удаленных записей истории сессии
     */
    Flux<ImageGenerationHistory> findBySessionIdAndDeletedFalseOrderByCreatedAtAsc(Long sessionId);

    /**
     * Найти последнюю запись истории сессии (с самой поздней датой создания).
     *
     * @param sessionId идентификатор сессии
     * @return последняя запись истории или пустой результат
     */
    Mono<ImageGenerationHistory> findFirstBySessionIdOrderByCreatedAtDesc(Long sessionId);

    /**
     * Найти последнюю не удаленную запись истории сессии (с самой поздней датой создания).
     *
     * @param sessionId идентификатор сессии
     * @return последняя не удаленная запись истории или пустой результат
     */
    Mono<ImageGenerationHistory> findFirstBySessionIdAndDeletedFalseOrderByCreatedAtDesc(Long sessionId);

    /**
     * Подсчитать количество записей истории в сессии.
     *
     * @param sessionId идентификатор сессии
     * @return количество записей истории
     */
    Mono<Long> countBySessionId(Long sessionId);

    /**
     * Подсчитать количество не удаленных записей истории в сессии.
     *
     * @param sessionId идентификатор сессии
     * @return количество не удаленных записей истории
     */
    Mono<Long> countBySessionIdAndDeletedFalse(Long sessionId);

    /**
     * Удалить все записи истории сессии.
     *
     * @param sessionId идентификатор сессии
     * @return количество удаленных записей
     */
    Mono<Integer> deleteBySessionId(Long sessionId);

    /**
     * Пометить все не удаленные записи истории сессии как удаленные (soft delete).
     *
     * @param sessionId идентификатор сессии
     * @return количество помеченных записей
     */
    @Query("UPDATE troyka.image_generation_history SET deleted = true WHERE session_id = :sessionId AND deleted = false")
    Mono<Integer> markAsDeletedBySessionId(Long sessionId);

    /**
     * Сохранить запись истории с правильным приведением JSONB.
     * Использует кастомный SQL-запрос для корректной работы с JSONB полями.
     *
     * @param userId идентификатор пользователя
     * @param imageUrlsJson JSON строка с URL сгенерированных изображений
     * @param prompt промпт
     * @param sessionId идентификатор сессии
     * @param inputImageUrlsJson JSON строка с URL входных изображений
     * @param styleId идентификатор стиля (по умолчанию 1 - Без стиля)
     * @param modelType тип модели (null для старых моделей)
     * @param resolution разрешение (null для старых моделей)
     * @return сохраненная запись
     */
    @Query("INSERT INTO troyka.image_generation_history (user_id, image_urls, prompt, created_at, session_id, input_image_urls, style_id, aspect_ratio, model_type, resolution, points_cost) " +
           "VALUES (:userId, :imageUrlsJson::jsonb, :prompt, :createdAt, :sessionId, :inputImageUrlsJson::jsonb, :styleId, :aspectRatio, :modelType, :resolution, :pointsCost) " +
           "RETURNING *")
    Mono<ImageGenerationHistory> saveWithJsonb(Long userId, String imageUrlsJson, String prompt, 
                                               LocalDateTime createdAt, Long sessionId, 
                                               String inputImageUrlsJson, Long styleId, String aspectRatio,
                                               String modelType, String resolution, Integer pointsCost);

    /**
     * Найти запись истории по идентификатору запроса Fal.ai.
     *
     * @param falRequestId идентификатор запроса в очереди Fal.ai
     * @return запись истории или пустой результат
     */
    Mono<ImageGenerationHistory> findByFalRequestId(String falRequestId);

    /**
     * Найти все активные записи истории пользователя (в очереди или обрабатываются).
     *
     * @param userId идентификатор пользователя
     * @param queueStatuses список статусов для поиска
     * @return поток активных записей истории пользователя
     */
    Flux<ImageGenerationHistory> findByUserIdAndQueueStatusIn(Long userId, List<String> queueStatuses);

    /**
     * Найти все активные записи истории сессии (в очереди или обрабатываются).
     *
     * @param sessionId идентификатор сессии
     * @param queueStatuses список статусов для поиска
     * @return поток активных записей истории сессии
     */
    Flux<ImageGenerationHistory> findBySessionIdAndQueueStatusIn(Long sessionId, List<String> queueStatuses);

    /**
     * Обновить запись истории с правильным приведением JSONB полей.
     * Использует кастомный SQL-запрос для корректной работы с JSONB полями при UPDATE.
     *
     * @param id идентификатор записи
     * @param imageUrlsJson JSON строка с URL сгенерированных изображений
     * @param inputImageUrlsJson JSON строка с URL входных изображений
     * @param queueStatus статус очереди
     * @param queuePosition позиция в очереди
     * @param updatedAt время обновления
     * @return обновленная запись
     */
    @Query("UPDATE troyka.image_generation_history " +
           "SET image_urls = :imageUrlsJson::jsonb, " +
           "    input_image_urls = CASE WHEN :inputImageUrlsJson IS NULL THEN NULL ELSE :inputImageUrlsJson::jsonb END, " +
           "    queue_status = :queueStatus, " +
           "    queue_position = :queuePosition, " +
           "    updated_at = :updatedAt " +
           "WHERE id = :id " +
           "RETURNING *")
    Mono<ImageGenerationHistory> updateWithJsonb(Long id, String imageUrlsJson, String inputImageUrlsJson,
                                                  String queueStatus, Integer queuePosition, LocalDateTime updatedAt);

    /**
     * Сохранить запись истории для очереди с правильным приведением JSONB полей.
     * Использует кастомный SQL-запрос для корректной работы с JSONB полями при INSERT.
     *
     * @param userId идентификатор пользователя
     * @param imageUrlsJson JSON строка с URL сгенерированных изображений (пустой массив для новой записи)
     * @param prompt промпт
     * @param createdAt время создания
     * @param sessionId идентификатор сессии
     * @param inputImageUrlsJson JSON строка с URL входных изображений (может быть null)
     * @param styleId идентификатор стиля
     * @param aspectRatio соотношение сторон
     * @param modelType тип модели (null для старых моделей)
     * @param resolution разрешение (null для старых моделей)
     * @param falRequestId идентификатор запроса в очереди Fal.ai
     * @param queueStatus статус очереди
     * @param queuePosition позиция в очереди
     * @param numImages количество запрошенных изображений
     * @param updatedAt время обновления
     * @return сохраненная запись
     */
    @Query("INSERT INTO troyka.image_generation_history " +
           "(user_id, image_urls, prompt, created_at, session_id, input_image_urls, style_id, aspect_ratio, model_type, resolution, " +
           "fal_request_id, queue_status, queue_position, num_images, points_cost, updated_at) " +
           "VALUES (:userId, :imageUrlsJson::jsonb, :prompt, :createdAt, :sessionId, " +
           "CASE WHEN :inputImageUrlsJson IS NULL THEN NULL ELSE :inputImageUrlsJson::jsonb END, " +
           ":styleId, :aspectRatio, :modelType, :resolution, :falRequestId, :queueStatus, :queuePosition, :numImages, :pointsCost, :updatedAt) " +
           "RETURNING *")
    Mono<ImageGenerationHistory> saveQueueRequest(Long userId, String imageUrlsJson, String prompt,
                                                   LocalDateTime createdAt, Long sessionId, String inputImageUrlsJson,
                                                   Long styleId, String aspectRatio, String modelType, String resolution,
                                                   String falRequestId, String queueStatus, Integer queuePosition, Integer numImages,
                                                   Integer pointsCost, LocalDateTime updatedAt);
}