package ru.oparin.troyka.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;

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
     * Найти все записи истории пользователя из конкретной сессии, отсортированные по дате создания (новые первые).
     *
     * @param userId идентификатор пользователя
     * @param sessionId идентификатор сессии
     * @return поток записей истории пользователя из сессии
     */
    Flux<ImageGenerationHistory> findByUserIdAndSessionIdOrderByCreatedAtDesc(Long userId, Long sessionId);

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
     * Найти последнюю запись истории сессии (с самой поздней датой создания).
     *
     * @param sessionId идентификатор сессии
     * @return последняя запись истории или пустой результат
     */
    Mono<ImageGenerationHistory> findFirstBySessionIdOrderByCreatedAtDesc(Long sessionId);

    /**
     * Подсчитать количество записей истории в сессии.
     *
     * @param sessionId идентификатор сессии
     * @return количество записей истории
     */
    Mono<Long> countBySessionId(Long sessionId);

    /**
     * Удалить все записи истории сессии.
     *
     * @param sessionId идентификатор сессии
     * @return количество удаленных записей
     */
    Mono<Integer> deleteBySessionId(Long sessionId);

    /**
     * Сохранить запись истории с правильным приведением JSONB.
     * Использует кастомный SQL-запрос для корректной работы с JSONB полями.
     *
     * @param userId идентификатор пользователя
     * @param imageUrlsJson JSON строка с URL сгенерированных изображений
     * @param prompt промпт
     * @param sessionId идентификатор сессии
     * @param inputImageUrlsJson JSON строка с URL входных изображений
     * @param description описание изображения от ИИ (может быть null)
     * @param styleId идентификатор стиля (по умолчанию 1 - Без стиля)
     * @return сохраненная запись
     */
    @Query("INSERT INTO troyka.image_generation_history (user_id, image_urls, prompt, created_at, session_id, input_image_urls, description, style_id) " +
           "VALUES (:userId, :imageUrlsJson::jsonb, :prompt, :createdAt, :sessionId, :inputImageUrlsJson::jsonb, :description, :styleId) " +
           "RETURNING *")
    Mono<ImageGenerationHistory> saveWithJsonb(Long userId, String imageUrlsJson, String prompt, 
                                               java.time.LocalDateTime createdAt, Long sessionId, 
                                               String inputImageUrlsJson, String description, Long styleId);
}