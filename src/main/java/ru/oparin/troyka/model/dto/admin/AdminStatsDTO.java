package ru.oparin.troyka.model.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO для статистики в админ-панели.
 * Содержит агрегированную информацию о пользователях, платежах и регистрациях.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsDTO {
    
    /**
     * Общее количество пользователей в системе.
     */
    private Long totalUsers;
    
    /**
     * Общее количество платежей в системе.
     */
    private Long totalPayments;
    
    /**
     * Общая выручка от всех успешно оплаченных платежей.
     */
    private BigDecimal totalRevenue;
    
    /**
     * Выручка за сегодня (с начала текущего дня).
     */
    private BigDecimal todayRevenue;
    
    /**
     * Выручка за последние 7 дней.
     */
    private BigDecimal weekRevenue;
    
    /**
     * Выручка за последние 30 дней.
     */
    private BigDecimal monthRevenue;
    
    /**
     * Выручка за последний год.
     */
    private BigDecimal yearRevenue;
    
    /**
     * Количество регистраций пользователей за сегодня (с начала текущего дня).
     */
    private Long todayRegistrations;
    
    /**
     * Количество регистраций пользователей за последние 7 дней.
     */
    private Long weekRegistrations;
    
    /**
     * Количество регистраций пользователей за последние 30 дней.
     */
    private Long monthRegistrations;
    
    /**
     * Количество регистраций пользователей за последний год.
     */
    private Long yearRegistrations;
}

