package ru.oparin.troyka.model.dto.payment;

import lombok.Data;

@Data
public class PaymentResponse {
    private String paymentUrl;
    private String orderId;
    private Double amount;
    private String status;
}