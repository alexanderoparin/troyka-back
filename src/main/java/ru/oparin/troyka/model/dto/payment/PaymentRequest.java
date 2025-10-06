package ru.oparin.troyka.model.dto.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * DTO для запроса создания платежа
 */
@Data
public class PaymentRequest {
    
    /**
     * Сумма платежа в рублях
     */
    @NotNull
    @Positive
    private Double amount;

    /**
     * Описание платежа
     */
    @NotNull
    private String description;

    /**
     * Уникальный идентификатор заказа (если не указан, генерируется автоматически)
     */
    private String orderId;

    /**
     * ID пользователя, создающего платеж
     */
    private Long userId;

    /**
     * Количество поинтов, которые будут начислены
     */
    private Integer credits;
}