package ru.oparin.troyka.model.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO для статистики генераций конкретного пользователя.
 * Содержит информацию о количестве генераций по моделям и разрешениям за период.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatisticsDTO {
    
    /**
     * ID пользователя.
     */
    private Long userId;
    
    /**
     * Имя пользователя.
     */
    private String username;
    
    /**
     * Начало периода статистики.
     */
    private LocalDateTime startDate;
    
    /**
     * Конец периода статистики.
     */
    private LocalDateTime endDate;
    
    /**
     * Количество генераций по обычной модели (NANO_BANANA).
     */
    private Long regularModelCount;
    
    /**
     * Количество генераций по ПРО модели (NANO_BANANA_PRO).
     */
    private Long proModelCount;
    
    /**
     * Количество генераций по ПРО модели, разбитое по разрешениям.
     * Ключ - разрешение (1K, 2K, 4K), значение - количество генераций.
     */
    private Map<String, Long> proModelByResolution;
    
    /**
     * Общее количество генераций за период.
     */
    private Long totalCount;
    
    /**
     * Общее количество потраченных поинтов за период.
     */
    private Long totalPointsSpent;
    
    /**
     * Количество потраченных поинтов на обычную модель (NANO_BANANA).
     */
    private Long regularModelPointsSpent;
    
    /**
     * Количество потраченных поинтов на ПРО модель (NANO_BANANA_PRO).
     */
    private Long proModelPointsSpent;
    
    /**
     * Количество потраченных поинтов на ПРО модель, разбитое по разрешениям.
     * Ключ - разрешение (1K, 2K, 4K), значение - количество поинтов.
     */
    private Map<String, Long> proModelPointsByResolution;
}

