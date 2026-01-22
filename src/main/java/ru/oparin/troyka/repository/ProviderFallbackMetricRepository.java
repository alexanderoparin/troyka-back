package ru.oparin.troyka.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.ProviderFallbackMetric;

import java.time.LocalDateTime;

/**
 * Репозиторий для работы с метриками fallback переключений между провайдерами.
 */
@Repository
public interface ProviderFallbackMetricRepository extends R2dbcRepository<ProviderFallbackMetric, Long> {

    /**
     * Подсчитать количество fallback переключений за период.
     *
     * @param startDate начало периода (включительно)
     * @param endDate   конец периода (включительно)
     * @return количество переключений
     */
    @Query("SELECT COUNT(*) FROM troyka.provider_fallback_metrics " +
           "WHERE created_at >= :startDate AND created_at <= :endDate")
    Mono<Long> countByPeriod(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Подсчитать количество fallback переключений для конкретного активного провайдера за период.
     *
     * @param activeProvider код активного провайдера
     * @param startDate      начало периода (включительно)
     * @param endDate        конец периода (включительно)
     * @return количество переключений
     */
    @Query("SELECT COUNT(*) FROM troyka.provider_fallback_metrics " +
           "WHERE active_provider = :activeProvider " +
           "AND created_at >= :startDate AND created_at <= :endDate")
    Mono<Long> countByActiveProviderAndPeriod(String activeProvider, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Получить все метрики за период.
     *
     * @param startDate начало периода (включительно)
     * @param endDate   конец периода (включительно)
     * @return поток метрик
     */
    @Query("SELECT * FROM troyka.provider_fallback_metrics " +
           "WHERE created_at >= :startDate AND created_at <= :endDate " +
           "ORDER BY created_at DESC")
    Flux<ProviderFallbackMetric> findByPeriod(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Получить последние N метрик.
     *
     * @param limit количество метрик
     * @return поток метрик
     */
    @Query("SELECT * FROM troyka.provider_fallback_metrics " +
           "ORDER BY created_at DESC LIMIT :limit")
    Flux<ProviderFallbackMetric> findRecent(int limit);
}
