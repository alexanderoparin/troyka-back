package ru.oparin.troyka.model.dto.payment;

import lombok.Data;

@Data
public class PaymentResponse {
    private String paymentUrl;
    private String paymentId; // ID платежа из БД
    private Double amount;
    private String status;
}