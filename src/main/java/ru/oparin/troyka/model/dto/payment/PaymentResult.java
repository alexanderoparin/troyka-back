package ru.oparin.troyka.model.dto.payment;

import lombok.Data;

@Data
public class PaymentResult {
    private String outSum;
    private String invId;
    private String signature;
    private String culture;
    private String email;
}