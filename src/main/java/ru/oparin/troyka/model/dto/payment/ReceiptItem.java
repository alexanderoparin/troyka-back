package ru.oparin.troyka.model.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO для позиции в чеке фискализации Robokassa
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptItem {
    
    /**
     * Наименование товара/услуги
     */
    @JsonProperty("name")
    private String name;
    
    /**
     * Количество товара
     */
    @JsonProperty("quantity")
    private Integer quantity;
    
    /**
     * Сумма позиции в рублях (за все количество)
     */
    @JsonProperty("sum")
    private BigDecimal sum;
    
    /**
     * Способ расчета
     * По умолчанию: service (услуга)
     */
    @JsonProperty("payment_method")
    @Builder.Default
    private String paymentMethod = "full_prepayment";

    /**
     * Способ расчета
     * По умолчанию: service (услуга)
     */
    @JsonProperty("payment_object")
    @Builder.Default
    private String paymentObject = "service";
    
    /**
     * Налоговая ставка
     * По умолчанию: none (без НДС)
     */
    @JsonProperty("tax")
    @Builder.Default
    private String tax = "none";
}
