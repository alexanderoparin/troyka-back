package ru.oparin.troyka.model.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO для статистики fallback переключений между провайдерами.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderFallbackStatsDTO {

    /**
     * Общее количество fallback переключений за сегодня.
     */
    private Long todayCount;

    /**
     * Общее количество fallback переключений за последние 7 дней.
     */
    private Long last7DaysCount;

    /**
     * Общее количество fallback переключений за последние 30 дней.
     */
    private Long last30DaysCount;

    /**
     * Количество fallback переключений по активным провайдерам за последние 30 дней.
     * Ключ - код провайдера (FAL_AI, LAOZHANG_AI), значение - количество переключений.
     */
    private Map<String, Long> countByActiveProvider;

    /**
     * Количество fallback переключений по типам ошибок за последние 30 дней.
     * Ключ - тип ошибки (TIMEOUT, CONNECTION_ERROR, HTTP_5XX и т.д.), значение - количество.
     */
    private Map<String, Long> countByErrorType;
}
