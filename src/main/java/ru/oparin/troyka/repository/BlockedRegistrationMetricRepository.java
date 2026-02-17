package ru.oparin.troyka.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.BlockedRegistrationMetric;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Репозиторий для работы с метриками блокированных регистраций.
 */
public interface BlockedRegistrationMetricRepository extends ReactiveCrudRepository<BlockedRegistrationMetric, Long> {

    /**
     * Найти все метрики, отсортированные по дате создания (новые первыми).
     */
    Flux<BlockedRegistrationMetric> findAllByOrderByCreatedAtDesc();

    /**
     * Подсчитать количество метрик за период.
     *
     * @param startDate начало периода
     * @param endDate конец периода
     * @return количество метрик
     */
    @Query("SELECT COUNT(*) FROM troyka.blocked_registration_metrics WHERE created_at >= :startDate AND created_at <= :endDate")
    Mono<Long> countByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Подсчитать количество метрик по домену за период.
     *
     * @param domain домен email
     * @param startDate начало периода
     * @param endDate конец периода
     * @return количество метрик
     */
    @Query("SELECT COUNT(*) FROM troyka.blocked_registration_metrics WHERE email_domain = :domain AND created_at >= :startDate AND created_at <= :endDate")
    Mono<Long> countByEmailDomainAndCreatedAtBetween(String domain, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Подсчитать количество метрик по IP адресу за период.
     *
     * @param ipAddress IP адрес
     * @param startDate начало периода
     * @param endDate конец периода
     * @return количество метрик
     */
    @Query("SELECT COUNT(*) FROM troyka.blocked_registration_metrics WHERE ip_address = :ipAddress AND created_at >= :startDate AND created_at <= :endDate")
    Mono<Long> countByIpAddressAndCreatedAtBetween(String ipAddress, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Найти все уникальные домены.
     */
    @Query("SELECT DISTINCT email_domain FROM troyka.blocked_registration_metrics ORDER BY email_domain")
    Flux<String> findAllDistinctEmailDomains();

    /**
     * Найти последние метрики с лимитом.
     *
     * @param limit максимальное количество записей
     * @return поток метрик
     */
    @Query("SELECT * FROM troyka.blocked_registration_metrics ORDER BY created_at DESC LIMIT :limit")
    Flux<BlockedRegistrationMetric> findRecentMetrics(int limit);

    /**
     * Подсчитать количество метрик по методу регистрации за период.
     */
    @Query("SELECT COUNT(*) FROM troyka.blocked_registration_metrics WHERE registration_method = :registrationMethod AND created_at >= :startDate AND created_at <= :endDate")
    Mono<Long> countByRegistrationMethodAndCreatedAtBetween(String registrationMethod, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Найти метрики за период с указанными методами регистрации.
     */
    @Query("SELECT * FROM troyka.blocked_registration_metrics WHERE registration_method IN (:methods) AND created_at >= :startDate AND created_at <= :endDate")
    Flux<BlockedRegistrationMetric> findByCreatedAtBetweenAndRegistrationMethodIn(LocalDateTime startDate, LocalDateTime endDate, List<String> methods);
}
