package ru.oparin.troyka.repository;

import org.springframework.data.domain.Pageable;
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
     * Найти все записи истории сессии, отсортированные по номеру итерации (по порядку диалога).
     *
     * @param sessionId идентификатор сессии
     * @param pageable параметры пагинации
     * @return поток записей истории сессии
     */
    Flux<ImageGenerationHistory> findBySessionIdOrderByIterationNumberAsc(Long sessionId, Pageable pageable);

    /**
     * Найти все записи истории сессии, отсортированные по номеру итерации (по порядку диалога).
     *
     * @param sessionId идентификатор сессии
     * @return поток всех записей истории сессии
     */
    Flux<ImageGenerationHistory> findBySessionIdOrderByIterationNumberAsc(Long sessionId);

    /**
     * Найти последнюю запись истории сессии (с наибольшим номером итерации).
     *
     * @param sessionId идентификатор сессии
     * @return последняя запись истории или пустой результат
     */
    Mono<ImageGenerationHistory> findFirstBySessionIdOrderByIterationNumberDesc(Long sessionId);

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
}