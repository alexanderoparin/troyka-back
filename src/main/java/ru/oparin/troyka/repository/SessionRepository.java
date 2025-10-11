package ru.oparin.troyka.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.Session;

/**
 * Repository для работы с сессиями генерации изображений.
 * Предоставляет методы для CRUD операций и специфичные запросы для сессий.
 * Использует R2DBC для реактивного взаимодействия с базой данных.
 */
@Repository
public interface SessionRepository extends ReactiveCrudRepository<Session, Long> {

    /**
     * Найти все сессии пользователя с пагинацией, отсортированные по дате обновления (новые первые).
     * Используется для отображения списка сессий в интерфейсе с поддержкой ленивой загрузки.
     *
     * @param userId идентификатор пользователя
     * @param pageable параметры пагинации (номер страницы, размер страницы)
     * @return поток сессий пользователя
     */
    Flux<Session> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    /**
     * Найти все сессии пользователя без пагинации, отсортированные по дате обновления (новые первые).
     * Используется для получения полного списка сессий пользователя.
     *
     * @param userId идентификатор пользователя
     * @return поток всех сессий пользователя
     */
    Flux<Session> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /**
     * Найти сессию по ID и пользователю для проверки прав доступа.
     * Гарантирует, что пользователь может работать только со своими сессиями.
     *
     * @param id идентификатор сессии
     * @param userId идентификатор пользователя
     * @return сессия или пустой результат, если сессия не найдена или не принадлежит пользователю
     */
    Mono<Session> findByIdAndUserId(Long id, Long userId);

    /**
     * Подсчитать общее количество сессий у пользователя.
     * Используется для отображения статистики и проверки лимитов.
     *
     * @param userId идентификатор пользователя
     * @return количество сессий пользователя
     */
    Mono<Long> countByUserId(Long userId);

    /**
     * Обновить время последнего обновления сессии.
     * Вызывается при каждой генерации изображений в сессии для корректной сортировки.
     *
     * @param id идентификатор сессии
     * @param updatedAt новое время обновления
     * @return количество обновленных записей (должно быть 1)
     */
    @Query("UPDATE sessions SET updated_at = :updatedAt WHERE id = :id")
    Mono<Integer> updateUpdatedAt(Long id, java.time.Instant updatedAt);

    /**
     * Удалить сессию по ID и пользователю с проверкой прав доступа.
     * Гарантирует, что пользователь может удалить только свои сессии.
     *
     * @param id идентификатор сессии
     * @param userId идентификатор пользователя
     * @return количество удаленных записей (0 или 1)
     */
    Mono<Integer> deleteByIdAndUserId(Long id, Long userId);
}

