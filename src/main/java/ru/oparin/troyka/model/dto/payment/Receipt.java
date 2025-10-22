package ru.oparin.troyka.model.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для чека фискализации Robokassa
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Receipt {
    
    /**
     * Система налогообложения
     * По умолчанию: usn_income (УСН доходы)
     */
    @JsonProperty("sno")
    @Builder.Default
    private String sno = "usn_income";
    
    /**
     * Список позиций в чеке
     */
    @JsonProperty("items")
    private List<ReceiptItem> items;
}
