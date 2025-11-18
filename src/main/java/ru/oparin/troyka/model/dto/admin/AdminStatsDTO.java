package ru.oparin.troyka.model.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO для статистики в админ-панели.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsDTO {
    
    private Long totalUsers;
    private Long totalPayments;
    private BigDecimal totalRevenue;
    private BigDecimal todayRevenue;
    private BigDecimal weekRevenue;
    private BigDecimal monthRevenue;
    private Long paidPaymentsCount;
    private Long pendingPaymentsCount;
    private Long failedPaymentsCount;
    private BigDecimal yearRevenue;
}

