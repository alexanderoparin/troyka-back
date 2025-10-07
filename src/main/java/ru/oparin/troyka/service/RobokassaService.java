package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.payment.PaymentRequest;
import ru.oparin.troyka.model.dto.payment.PaymentResponse;
import ru.oparin.troyka.model.entity.Payment;
import ru.oparin.troyka.model.enums.PaymentStatus;
import ru.oparin.troyka.repository.PaymentRepository;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/**
 * Сервис для работы с платежной системой Робокасса
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobokassaService {

    private final PaymentRepository paymentRepository;
    private final UserPointsService userPointsService;

    /**
     * Логин мерчанта в Робокассе
     */
    @Value("${robokassa.merchant.login}")
    private String merchantLogin;

    /**
     * Пароль 1 для подписи запросов
     */
    @Value("${robokassa.password1}")
    private String password1;

    /**
     * Пароль 2 для проверки callback'ов
     */
    @Value("${robokassa.password2}")
    private String password2;

    /**
     * Режим тестирования
     */
    @Value("${robokassa.test}")
    private boolean isTest;

    /**
     * URL для успешной оплаты
     */
    @Value("${robokassa.success-url}")
    private String successUrl;

    /**
     * URL для неудачной оплаты
     */
    @Value("${robokassa.fail-url}")
    private String failUrl;

    /**
     * URL для получения результата оплаты (callback)
     */
    @Value("${robokassa.result-url}")
    private String resultUrl;

    /**
     * Создает платеж в системе Робокасса
     * 
     * @param request данные для создания платежа
     * @return ответ с URL для оплаты
     * @throws RuntimeException если произошла ошибка при создании платежа
     */
    public Mono<PaymentResponse> createPayment(PaymentRequest request) {
        // Используем количество поинтов из запроса
        Integer creditsAmount = request.getCredits();

        // Создаем платеж в БД без orderId (будет использоваться ID)
        Payment payment = Payment.builder()
                .userId(request.getUserId())
                .amount(BigDecimal.valueOf(request.getAmount()))
                .description(request.getDescription())
                .status(PaymentStatus.CREATED)
                .creditsAmount(creditsAmount)
                .build();

        return paymentRepository.save(payment)
                .flatMap(savedPayment -> {
                    try {
                        // Используем ID платежа как InvId для Robokassa
                        String invId = savedPayment.getId().toString();

                        // Создаем подпись для запроса
                        String signature = createSignature(request.getAmount(), invId);

                        // Формируем URL для оплаты
                        String paymentUrl = buildPaymentUrl(request.getAmount(), invId, request.getDescription(), signature);

                        // Обновляем платеж с подписью и URL
                        savedPayment.setRobokassaSignature(signature);
                        savedPayment.setPaymentUrl(paymentUrl);

                        return paymentRepository.save(savedPayment)
                                .map(updatedPayment -> {
                                    PaymentResponse response = new PaymentResponse();
                                    response.setPaymentUrl(paymentUrl);
                                    response.setPaymentId(invId);
                                    response.setAmount(request.getAmount());
                                    response.setStatus("created");

                                    log.info("Создан платеж для заказа: {}, сумма: {}", invId, request.getAmount());
                                    return response;
                                });
                    } catch (Exception e) {
                        // Если что-то пошло не так, отменяем заказ
                        log.error("Ошибка при создании URL для платежа {}: {}", savedPayment.getId(), e.getMessage());
                        return cancelPayment(savedPayment.getId())
                                .then(Mono.error(new RuntimeException("Ошибка создания платежа", e)));
                    }
                })
                .doOnError(error -> log.error("Ошибка создания платежа: {}", error.getMessage()));
    }

    /**
     * Отменяет платеж (устанавливает статус CANCELLED)
     * 
     * @param paymentId ID платежа для отмены
     * @return Mono<Void>
     */
    private Mono<Void> cancelPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .flatMap(payment -> {
                    payment.setStatus(PaymentStatus.CANCELLED);
                    return paymentRepository.save(payment);
                })
                .doOnSuccess(payment -> log.info("Платеж {} отменен", paymentId))
                .doOnError(error -> log.error("Ошибка отмены платежа {}: {}", paymentId, error.getMessage()))
                .then();
    }

    /**
     * Проверяет подпись платежа от Робокассы
     * 
     * @param outSum сумма платежа
     * @param invId идентификатор заказа
     * @param signature подпись от Робокассы
     * @return true если подпись верна, false в противном случае
     */
    public boolean verifyPayment(String outSum, String invId, String signature) {
        try {
            String checkString = String.format("%s:%s:%s", outSum, invId, password2);
            String calculatedSignature = md5(checkString);

            boolean isValid = calculatedSignature.equalsIgnoreCase(signature);
            log.info("Проверка платежа для заказа {}: {}", invId, isValid ? "УСПЕШНО" : "НЕУДАЧНО");

            if (isValid) {
                // Обновляем статус платежа в БД и начисляем поинты
                // Ищем платеж по ID (invId = ID платежа)
                Long paymentId = Long.parseLong(invId);
                paymentRepository.findById(paymentId)
                        .flatMap(payment -> {
                            payment.setStatus(PaymentStatus.PAID);
                            payment.setPaidAt(LocalDateTime.now());
                            payment.setRobokassaResponse(String.format("OutSum=%s, InvId=%s, Signature=%s", outSum, invId, signature));
                            return paymentRepository.save(payment);
                        })
                        .flatMap(payment -> {
                            // Начисляем поинты пользователю
                            if (payment.getCreditsAmount() != null && payment.getCreditsAmount() > 0) {
                                return userPointsService.addPointsToUser(payment.getUserId(), payment.getCreditsAmount())
                                        .map(userPoints -> {
                                            log.info("Начислено {} поинтов пользователю {} (платеж {})", 
                                                    payment.getCreditsAmount(), payment.getUserId(), payment.getId());
                                            return payment;
                                        });
                            } else {
                                log.warn("Не удалось начислить поинты: creditsAmount = {} для платежа {}", 
                                        payment.getCreditsAmount(), payment.getId());
                                return Mono.just(payment);
                            }
                        })
                        .subscribe(
                                updatedPayment -> log.info("Платеж обработан успешно: {}", updatedPayment.getId()),
                                error -> log.error("Ошибка обработки платежа: {}", error.getMessage())
                        );
            }

            return isValid;

        } catch (Exception e) {
            log.error("Ошибка проверки платежа: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Создает подпись для запроса к Робокассе
     * Согласно документации: MerchantLogin:OutSum:InvId:Password#1
     * где InvId может быть пустым, но должен присутствовать
     * 
     * @param amount сумма платежа
     * @param orderId идентификатор заказа
     * @return MD5 подпись
     */
    private String createSignature(Double amount, String orderId) {
        String checkString = String.format("%s:%s:%s:%s", merchantLogin, amount, orderId, password1);
        return md5(checkString);
    }

    /**
     * Строит URL для оплаты в Робокассе с правильным URL кодированием
     * 
     * @param amount сумма платежа
     * @param orderId идентификатор заказа
     * @param description описание платежа
     * @param signature подпись запроса
     * @return URL для перенаправления на оплату
     */
    private String buildPaymentUrl(Double amount, String orderId, String description, String signature) {
        StringBuilder url = new StringBuilder();
        url.append("https://auth.robokassa.ru/Merchant/Index.aspx");
        url.append("?MerchantLogin=").append(merchantLogin);
        url.append("&OutSum=").append(amount);
        url.append("&InvId=").append(orderId);
        url.append("&Description=").append(URLEncoder.encode(description, StandardCharsets.UTF_8));
        url.append("&SignatureValue=").append(signature);
        url.append("&Culture=ru");
        url.append("&Encoding=utf-8");
        url.append("&ResultURL=").append(URLEncoder.encode(resultUrl, StandardCharsets.UTF_8));
        url.append("&SuccessURL=").append(URLEncoder.encode(successUrl, StandardCharsets.UTF_8));
        url.append("&FailURL=").append(URLEncoder.encode(failUrl, StandardCharsets.UTF_8));

        if (isTest) {
            url.append("&IsTest=1");
        }

        return url.toString();
    }

    /**
     * Вычисляет MD5 хеш строки
     * 
     * @param input входная строка
     * @return MD5 хеш в шестнадцатеричном формате
     * @throws RuntimeException если алгоритм MD5 недоступен
     */
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