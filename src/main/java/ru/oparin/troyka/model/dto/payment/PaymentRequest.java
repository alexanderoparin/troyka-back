package ru.oparin.troyka.model.dto.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PaymentRequest {
    @NotNull
    @Positive
    private Double amount;

    @NotNull
    private String description;

    private String orderId;
}