package ru.oparin.troyka.model.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO для статистики блокированных регистраций.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockedRegistrationStatsDTO {

    /**
     * Общее количество блокированных регистраций за сегодня.
     */
    private Long todayCount;

    /**
     * Общее количество блокированных регистраций за последние 7 дней.
     */
    private Long last7DaysCount;

    /**
     * Общее количество блокированных регистраций за последние 30 дней.
     */
    private Long last30DaysCount;

    /**
     * Количество блокировок по IP (превышение лимита регистраций с одного IP) за сегодня.
     */
    private Long ipRateLimitTodayCount;

    /**
     * Количество блокировок по IP за последние 7 дней.
     */
    private Long ipRateLimitLast7DaysCount;

    /**
     * Количество блокировок по IP за последние 30 дней.
     */
    private Long ipRateLimitLast30DaysCount;

    /**
     * Количество блокированных регистраций по доменам (топ доменов).
     * Ключ - домен, значение - количество попыток.
     */
    private Map<String, Long> countByDomain;

    /**
     * Количество блокированных регистраций по IP адресам (топ IP).
     * Ключ - IP адрес, значение - количество попыток.
     */
    private Map<String, Long> countByIpAddress;

    /**
     * Последние блокированные регистрации.
     */
    private List<BlockedRegistrationMetricDTO> recentMetrics;
}
