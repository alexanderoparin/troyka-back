package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.oparin.troyka.model.dto.payment.PaymentRequest;
import ru.oparin.troyka.model.dto.payment.PaymentResponse;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobokassaService {

    @Value("${robokassa.merchant.login}")
    private String merchantLogin;

    @Value("${robokassa.password1}")
    private String password1;

    @Value("${robokassa.password2}")
    private String password2;

    @Value("${robokassa.test}")
    private boolean isTest;

    @Value("${robokassa.success-url}")
    private String successUrl;

    @Value("${robokassa.fail-url}")
    private String failUrl;

    @Value("${robokassa.result-url}")
    private String resultUrl;

    public PaymentResponse createPayment(PaymentRequest request) {
        try {
            String orderId = request.getOrderId() != null ? request.getOrderId() : UUID.randomUUID().toString();

            // Создаем подпись для запроса
            String signature = createSignature(request.getAmount(), orderId);

            // Формируем URL для оплаты
            String paymentUrl = buildPaymentUrl(request.getAmount(), orderId, request.getDescription(), signature);

            PaymentResponse response = new PaymentResponse();
            response.setPaymentUrl(paymentUrl);
            response.setOrderId(orderId);
            response.setAmount(request.getAmount());
            response.setStatus("created");

            log.info("Создан платеж для заказа: {}, сумма: {}", orderId, request.getAmount());

            return response;

        } catch (Exception e) {
            log.error("Ошибка создания платежа: {}", e.getMessage());
            throw new RuntimeException("Ошибка создания платежа", e);
        }
    }

    public boolean verifyPayment(String outSum, String invId, String signature) {
        try {
            String checkString = String.format("%s:%s:%s", outSum, invId, password2);
            String calculatedSignature = md5(checkString);

            boolean isValid = calculatedSignature.equalsIgnoreCase(signature);
            log.info("Проверка платежа для заказа {}: {}", invId, isValid ? "УСПЕШНО" : "НЕУДАЧНО");

            return isValid;

        } catch (Exception e) {
            log.error("Ошибка проверки платежа: {}", e.getMessage());
            return false;
        }
    }

    private String createSignature(Double amount, String orderId) {
        String checkString = String.format("%s:%s:%s", merchantLogin, amount, orderId) + ":" + password1;
        return md5(checkString);
    }

    private String buildPaymentUrl(Double amount, String orderId, String description, String signature) {
        StringBuilder url = new StringBuilder();
        url.append("https://auth.robokassa.ru/Merchant/Index.aspx");
        url.append("?MerchantLogin=").append(merchantLogin);
        url.append("&OutSum=").append(amount);
        url.append("&InvId=").append(orderId);
        url.append("&Description=").append(description);
        url.append("&SignatureValue=").append(signature);
        url.append("&Culture=ru");
        url.append("&Encoding=utf-8");
        url.append("&ResultURL=").append(resultUrl);
        url.append("&SuccessURL=").append(successUrl);
        url.append("&FailURL=").append(failUrl);

        if (isTest) {
            url.append("&IsTest=1");
        }

        return url.toString();
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();

            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Алгоритм MD5 не найден", e);
        }
    }
}