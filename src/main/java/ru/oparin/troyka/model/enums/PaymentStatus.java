package ru.oparin.troyka.model.enums;

/**
 * Статусы платежей в системе
 */
public enum PaymentStatus {
    /**
     * Платеж создан
     */
    CREATED,
    
    /**
     * Ожидает оплаты
     */
    PENDING,
    
    /**
     * Оплачен
     */
    PAID,
    
    /**
     * Неудачный
     */
    FAILED,
    
    /**
     * Отменен
     */
    CANCELLED,
    
    /**
     * Возвращен
     */
    REFUNDED
}
