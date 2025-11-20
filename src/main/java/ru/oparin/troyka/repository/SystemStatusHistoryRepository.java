package ru.oparin.troyka.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.SystemStatusHistory;
import ru.oparin.troyka.model.enums.SystemStatus;

/**
 * Репозиторий для работы с историей статусов системы.
 */
@Repository
public interface SystemStatusHistoryRepository extends ReactiveCrudRepository<SystemStatusHistory, Long> {

    /**
     * Получить последнюю запись истории статуса системы.
     * Используется для получения текущего статуса и последнего сообщения.
     *
     * @return последняя запись истории или пустой Mono, если записей нет
     */
    @Query("SELECT * FROM troyka.system_status_history ORDER BY created_at DESC LIMIT 1")
    Mono<SystemStatusHistory> findLatest();

    /**
     * Получить последнюю запись истории статуса системы с определенным статусом.
     * Используется для проверки, был ли уже установлен определенный статус.
     *
     * @param status статус для поиска
     * @return последняя запись с указанным статусом или пустой Mono
     */
    @Query("SELECT * FROM troyka.system_status_history WHERE status = :status ORDER BY created_at DESC LIMIT 1")
    Mono<SystemStatusHistory> findLatestByStatus(SystemStatus status);
}

