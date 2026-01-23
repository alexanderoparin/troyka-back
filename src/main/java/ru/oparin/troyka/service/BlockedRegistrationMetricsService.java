package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.admin.BlockedRegistrationMetricDTO;
import ru.oparin.troyka.model.dto.admin.BlockedRegistrationStatsDTO;
import ru.oparin.troyka.model.entity.BlockedRegistrationMetric;
import ru.oparin.troyka.repository.BlockedRegistrationMetricRepository;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ru.oparin.troyka.config.DatabaseConfig.withRetry;

/**
 * Сервис для работы с метриками блокированных регистраций.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlockedRegistrationMetricsService {

    private static final int DAYS_IN_WEEK = 7;
    private static final int DAYS_IN_MONTH = 30;
    private static final int RECENT_METRICS_LIMIT = 50;

    private final BlockedRegistrationMetricRepository repository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    /**
     * Записать метрику блокированной регистрации.
     *
     * @param email email адрес
     * @param emailDomain домен email
     * @param username имя пользователя (может быть null)
     * @param ipAddress IP адрес
     * @param userAgent User-Agent
     * @param registrationMethod метод регистрации (EMAIL или TELEGRAM)
     * @return сохраненная метрика
     */
    public Mono<BlockedRegistrationMetric> recordBlockedRegistration(
            String email,
            String emailDomain,
            String username,
            String ipAddress,
            String userAgent,
            String registrationMethod) {

        BlockedRegistrationMetric metric = BlockedRegistrationMetric.builder()
                .email(email)
                .emailDomain(emailDomain)
                .username(username)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .registrationMethod(registrationMethod)
                .build();

        return withRetry(repository.save(metric))
                .doOnSuccess(saved -> log.debug("Метрика блокированной регистрации сохранена: id={}, domain={}, ip={}", 
                        saved.getId(), saved.getEmailDomain(), saved.getIpAddress()))
                .doOnError(error -> log.error("Ошибка сохранения метрики блокированной регистрации", error));
    }

    /**
     * Получить статистику блокированных регистраций.
     *
     * @return статистика
     */
    public Mono<BlockedRegistrationStatsDTO> getStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.with(LocalTime.MIN);
        LocalDateTime weekStart = now.minusDays(DAYS_IN_WEEK).with(LocalTime.MIN);
        LocalDateTime monthStart = now.minusDays(DAYS_IN_MONTH).with(LocalTime.MIN);

        Mono<Long> todayCountMono = withRetry(repository.countByCreatedAtBetween(todayStart, now));
        Mono<Long> weekCountMono = withRetry(repository.countByCreatedAtBetween(weekStart, now));
        Mono<Long> monthCountMono = withRetry(repository.countByCreatedAtBetween(monthStart, now));

        Mono<Map<String, Long>> countByDomainMono = getCountByDomain(monthStart, now);
        Mono<Map<String, Long>> countByIpMono = getCountByIpAddress(monthStart, now);
        Mono<List<BlockedRegistrationMetricDTO>> recentMetricsMono = getRecentMetrics();

        return Mono.zip(todayCountMono, weekCountMono, monthCountMono, countByDomainMono, countByIpMono, recentMetricsMono)
                .map(tuple -> BlockedRegistrationStatsDTO.builder()
                        .todayCount(tuple.getT1())
                        .last7DaysCount(tuple.getT2())
                        .last30DaysCount(tuple.getT3())
                        .countByDomain(tuple.getT4())
                        .countByIpAddress(tuple.getT5())
                        .recentMetrics(tuple.getT6())
                        .build());
    }

    /**
     * Получить количество блокированных регистраций по доменам за период.
     */
    private Mono<Map<String, Long>> getCountByDomain(LocalDateTime startDate, LocalDateTime endDate) {
        return repository.findAll()
                .filter(metric -> metric.getCreatedAt() != null 
                        && !metric.getCreatedAt().isBefore(startDate) 
                        && !metric.getCreatedAt().isAfter(endDate))
                .collectList()
                .map(metrics -> {
                    Map<String, Long> countMap = new HashMap<>();
                    for (BlockedRegistrationMetric metric : metrics) {
                        String domain = metric.getEmailDomain();
                        countMap.put(domain, countMap.getOrDefault(domain, 0L) + 1);
                    }
                    return countMap;
                });
    }

    /**
     * Получить количество блокированных регистраций по IP адресам за период.
     */
    private Mono<Map<String, Long>> getCountByIpAddress(LocalDateTime startDate, LocalDateTime endDate) {
        return repository.findAll()
                .filter(metric -> metric.getCreatedAt() != null 
                        && metric.getIpAddress() != null
                        && !metric.getCreatedAt().isBefore(startDate) 
                        && !metric.getCreatedAt().isAfter(endDate))
                .collectList()
                .map(metrics -> {
                    Map<String, Long> countMap = new HashMap<>();
                    for (BlockedRegistrationMetric metric : metrics) {
                        String ip = metric.getIpAddress();
                        countMap.put(ip, countMap.getOrDefault(ip, 0L) + 1);
                    }
                    return countMap;
                });
    }

    /**
     * Получить последние метрики.
     */
    private Mono<List<BlockedRegistrationMetricDTO>> getRecentMetrics() {
        return repository.findRecentMetrics(RECENT_METRICS_LIMIT)
                .map(metric -> BlockedRegistrationMetricDTO.builder()
                        .id(metric.getId())
                        .email(metric.getEmail())
                        .emailDomain(metric.getEmailDomain())
                        .username(metric.getUsername())
                        .ipAddress(metric.getIpAddress())
                        .userAgent(metric.getUserAgent())
                        .registrationMethod(metric.getRegistrationMethod())
                        .createdAt(metric.getCreatedAt())
                        .build())
                .collectList();
    }
}
