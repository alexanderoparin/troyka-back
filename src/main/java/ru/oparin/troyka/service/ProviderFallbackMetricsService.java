package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple5;
import ru.oparin.troyka.model.dto.admin.ProviderFallbackStatsDTO;
import ru.oparin.troyka.model.entity.ProviderFallbackMetric;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.repository.ProviderFallbackMetricRepository;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для управления метриками fallback переключений между провайдерами.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderFallbackMetricsService {

    private final ProviderFallbackMetricRepository repository;

    /**
     * Записать метрику fallback переключения.
     *
     * @param activeProvider  активный провайдер, который не смог выполнить запрос
     * @param fallbackProvider резервный провайдер, на который произошло переключение
     * @param errorType       тип ошибки
     * @param httpStatus      HTTP статус код (может быть null)
     * @param errorMessage    сообщение об ошибке
     * @param userId          идентификатор пользователя (может быть null)
     * @return сохраненная метрика
     */
    public Mono<ProviderFallbackMetric> recordFallback(
            GenerationProvider activeProvider,
            GenerationProvider fallbackProvider,
            String errorType,
            Integer httpStatus,
            String errorMessage,
            Long userId) {
        
        log.info("Запись метрики fallback: {} -> {}, errorType={}, httpStatus={}, userId={}",
                activeProvider, fallbackProvider, errorType, httpStatus, userId);

        ProviderFallbackMetric metric = ProviderFallbackMetric.builder()
                .activeProvider(activeProvider.getCode())
                .fallbackProvider(fallbackProvider.getCode())
                .errorType(errorType)
                .httpStatus(httpStatus)
                .errorMessage(errorMessage != null && errorMessage.length() > 500 
                        ? errorMessage.substring(0, 500) 
                        : errorMessage)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .build();

        return repository.save(metric)
                .doOnSuccess(m -> log.debug("Метрика fallback сохранена: id={}", m.getId()))
                .doOnError(error -> log.error("Ошибка при сохранении метрики fallback: {}", error.getMessage()));
    }

    /**
     * Подсчитать количество fallback переключений за сегодня.
     *
     * @return количество переключений
     */
    public Mono<Long> countToday() {
        LocalDateTime todayStart = LocalDateTime.now().with(LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.now().with(LocalTime.MAX);
        return repository.countByPeriod(todayStart, todayEnd);
    }

    /**
     * Подсчитать количество fallback переключений за последние 7 дней.
     *
     * @return количество переключений
     */
    public Mono<Long> countLast7Days() {
        LocalDateTime weekStart = LocalDateTime.now().minusDays(7).with(LocalTime.MIN);
        LocalDateTime now = LocalDateTime.now();
        return repository.countByPeriod(weekStart, now);
    }

    /**
     * Подсчитать количество fallback переключений за последние 30 дней.
     *
     * @return количество переключений
     */
    public Mono<Long> countLast30Days() {
        LocalDateTime monthStart = LocalDateTime.now().minusDays(30).with(LocalTime.MIN);
        LocalDateTime now = LocalDateTime.now();
        return repository.countByPeriod(monthStart, now);
    }

    /**
     * Подсчитать количество fallback переключений для конкретного активного провайдера за период.
     *
     * @param activeProvider активный провайдер
     * @param startDate      начало периода
     * @param endDate        конец периода
     * @return количество переключений
     */
    public Mono<Long> countByActiveProviderAndPeriod(
            GenerationProvider activeProvider,
            LocalDateTime startDate,
            LocalDateTime endDate) {
        return repository.countByActiveProviderAndPeriod(
                activeProvider.getCode(),
                startDate,
                endDate
        );
    }

    /**
     * Получить последние N метрик.
     *
     * @param limit количество метрик
     * @return поток метрик
     */
    public Flux<ProviderFallbackMetric> getRecent(int limit) {
        return repository.findRecent(limit);
    }

    /**
     * Получить статистику fallback переключений.
     *
     * @return статистика fallback
     */
    public Mono<ProviderFallbackStatsDTO> getFallbackStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.with(LocalTime.MIN);
        LocalDateTime weekStart = now.minusDays(7).with(LocalTime.MIN);
        LocalDateTime monthStart = now.minusDays(30).with(LocalTime.MIN);

        Mono<Long> todayCountMono = countToday();
        Mono<Long> last7DaysCountMono = countLast7Days();
        Mono<Long> last30DaysCountMono = countLast30Days();

        // Получаем метрики за последние 30 дней для группировки
        Mono<Map<String, Long>> countByActiveProviderMono = repository.findByPeriod(monthStart, now)
                .collectList()
                .map(metrics -> {
                    Map<String, Long> result = new HashMap<>();
                    metrics.forEach(metric -> {
                        String provider = metric.getActiveProvider();
                        result.put(provider, result.getOrDefault(provider, 0L) + 1L);
                    });
                    return result;
                })
                .defaultIfEmpty(new HashMap<>());

        Mono<Map<String, Long>> countByErrorTypeMono = repository.findByPeriod(monthStart, now)
                .collectList()
                .map(metrics -> {
                    Map<String, Long> result = new HashMap<>();
                    metrics.forEach(metric -> {
                        String errorType = metric.getErrorType();
                        result.put(errorType, result.getOrDefault(errorType, 0L) + 1L);
                    });
                    return result;
                })
                .defaultIfEmpty(new HashMap<>());

        Mono<Tuple5<Long, Long, Long, Map<String, Long>, Map<String, Long>>> zipResult = Mono.zip(
                todayCountMono,
                last7DaysCountMono,
                last30DaysCountMono,
                countByActiveProviderMono,
                countByErrorTypeMono
        );
        
        return zipResult.map(tuple -> ProviderFallbackStatsDTO.builder()
                .todayCount(tuple.getT1())
                .last7DaysCount(tuple.getT2())
                .last30DaysCount(tuple.getT3())
                .countByActiveProvider(tuple.getT4())
                .countByErrorType(tuple.getT5())
                .build());
    }
}
