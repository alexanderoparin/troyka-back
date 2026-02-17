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

    private static final List<String> EMAIL_METHODS = List.of("EMAIL", "TELEGRAM");

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

        Mono<Long> todayEmailMono = countEmailAndTelegramBetween(todayStart, now);
        Mono<Long> weekEmailMono = countEmailAndTelegramBetween(weekStart, now);
        Mono<Long> monthEmailMono = countEmailAndTelegramBetween(monthStart, now);

        Mono<Long> ipTodayMono = withRetry(repository.countByRegistrationMethodAndCreatedAtBetween("IP_RATE_LIMIT", todayStart, now));
        Mono<Long> ipWeekMono = withRetry(repository.countByRegistrationMethodAndCreatedAtBetween("IP_RATE_LIMIT", weekStart, now));
        Mono<Long> ipMonthMono = withRetry(repository.countByRegistrationMethodAndCreatedAtBetween("IP_RATE_LIMIT", monthStart, now));

        Mono<Map<String, Long>> countByDomainMono = getCountByDomain(monthStart, now);
        Mono<Map<String, Long>> countByIpMono = getCountByIpAddress(monthStart, now);
        Mono<List<BlockedRegistrationMetricDTO>> recentMetricsMono = getRecentMetrics();

        return Mono.zip(todayEmailMono, weekEmailMono, monthEmailMono,
                        ipTodayMono, ipWeekMono, ipMonthMono,
                        countByDomainMono, countByIpMono, recentMetricsMono)
                .map(tuple -> BlockedRegistrationStatsDTO.builder()
                        .todayCount(tuple.getT1())
                        .last7DaysCount(tuple.getT2())
                        .last30DaysCount(tuple.getT3())
                        .ipRateLimitTodayCount(tuple.getT4())
                        .ipRateLimitLast7DaysCount(tuple.getT5())
                        .ipRateLimitLast30DaysCount(tuple.getT6())
                        .countByDomain(tuple.getT7())
                        .countByIpAddress(tuple.getT8())
                        .recentMetrics(tuple.getT9())
                        .build());
    }

    private Mono<Long> countEmailAndTelegramBetween(LocalDateTime start, LocalDateTime end) {
        Mono<Long> email = withRetry(repository.countByRegistrationMethodAndCreatedAtBetween("EMAIL", start, end));
        Mono<Long> telegram = withRetry(repository.countByRegistrationMethodAndCreatedAtBetween("TELEGRAM", start, end));
        return Mono.zip(email, telegram).map(t -> t.getT1() + t.getT2());
    }

    /**
     * Получить количество блокированных регистраций по доменам за период (только EMAIL и TELEGRAM).
     */
    private Mono<Map<String, Long>> getCountByDomain(LocalDateTime startDate, LocalDateTime endDate) {
        return repository.findByCreatedAtBetweenAndRegistrationMethodIn(startDate, endDate, EMAIL_METHODS)
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
     * Получить количество блокированных регистраций по IP адресам за период (только EMAIL и TELEGRAM).
     */
    private Mono<Map<String, Long>> getCountByIpAddress(LocalDateTime startDate, LocalDateTime endDate) {
        return repository.findByCreatedAtBetweenAndRegistrationMethodIn(startDate, endDate, EMAIL_METHODS)
                .filter(metric -> metric.getIpAddress() != null)
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
