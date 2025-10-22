package ru.oparin.troyka.model.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для истории платежей пользователя
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistory {
    
    /**
     * ID платежа
     */
    private Integer id;
    
    /**
     * Сумма платежа в рублях
     */
    private Double amount;
    
    /**
     * Описание платежа
     */
    private String description;
    
    /**
     * Статус платежа
     */
    private String status;
    
    /**
     * Количество поинтов, которые были начислены
     */
    private Integer creditsAmount;
    
    /**
     * Время успешной оплаты (если оплачен)
     */
    private String paidAt;
    
    /**
     * Время создания платежа
     */
    private String createdAt;
    
    /**
     * Флаг тестового платежа
     */
    private Boolean isTest;
}
