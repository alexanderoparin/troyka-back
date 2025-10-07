package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.payment.PaymentHistory;
import ru.oparin.troyka.model.dto.payment.PaymentRequest;
import ru.oparin.troyka.model.dto.payment.PaymentResponse;
import ru.oparin.troyka.service.PaymentService;
import ru.oparin.troyka.service.RobokassaService;

import java.util.List;
import java.util.Map;

/**
 * Контроллер для работы с платежами через Робокассу
 */
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Платежи", description = "API для создания и обработки платежей через Робокассу")
public class PaymentController {

    private final PaymentService paymentService;
    private final RobokassaService robokassaService;

    @Operation(summary = "Создать платеж",
            description = "Создает новый платеж в системе Робокасса и возвращает URL для оплаты")
    @PostMapping("/create")
    public Mono<ResponseEntity<PaymentResponse>> createPayment(
            @Parameter(description = "Данные для создания платежа", required = true)
            @Valid @RequestBody PaymentRequest request) {
        return paymentService.createPayment(request)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка создания платежа: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    @Operation(summary = "Обработать результат платежа (GET)",
            description = "Callback эндпоинт для получения результата платежа от Робокассы через GET. " +
                    "Проверяет подпись и обновляет статус платежа в системе.")
    @GetMapping("/result")
    public Mono<ResponseEntity<String>> handleResultGet(
            @RequestParam Map<String, String> allParams) {
        
        return Mono.fromCallable(() -> {
            try {
                log.info("=== ROBOKASSA CALLBACK DEBUG (GET) ===");
                log.info("Все параметры: {}", allParams);
                log.info("=== END DEBUG ===");
                
                String outSum = allParams.get("OutSum");
                String invId = allParams.get("InvId");
                String signature = allParams.get("SignatureValue");
                String culture = allParams.get("Culture");
                String isTest = allParams.get("IsTest");

                log.info("OutSum: {}, InvId: {}, SignatureValue: {}, Culture: {}, IsTest: {}", 
                        outSum, invId, signature, culture, isTest);

                if (robokassaService.verifyPayment(outSum, invId, signature)) {
                    log.info("Платеж успешно проверен для заказа: {}", invId);
                    return ResponseEntity.ok("OK");
                } else {
                    log.warn("Проверка платежа не удалась для заказа: {}", invId);
                    return ResponseEntity.badRequest().body("FAIL");
                }

            } catch (Exception e) {
                log.error("Ошибка обработки результата платежа: {}", e.getMessage());
                return ResponseEntity.badRequest().body("ERROR");
            }
        });
    }

    @Operation(summary = "Обработать результат платежа (POST)",
            description = "Callback эндпоинт для получения результата платежа от Робокассы через POST. " +
                    "Проверяет подпись и обновляет статус платежа в системе.")
    @PostMapping("/result")
    public Mono<ResponseEntity<String>> handleResultPost(
            @RequestParam Map<String, String> allParams) {
        
        return Mono.fromCallable(() -> {
            try {
                log.info("=== ROBOKASSA CALLBACK DEBUG (POST) ===");
                log.info("Все параметры: {}", allParams);
                log.info("=== END DEBUG ===");
                
                String outSum = allParams.get("OutSum");
                String invId = allParams.get("InvId");
                String signature = allParams.get("SignatureValue");
                String culture = allParams.get("Culture");
                String isTest = allParams.get("IsTest");

                log.info("OutSum: {}, InvId: {}, SignatureValue: {}, Culture: {}, IsTest: {}", 
                        outSum, invId, signature, culture, isTest);

                if (robokassaService.verifyPayment(outSum, invId, signature)) {
                    log.info("Платеж успешно проверен для заказа: {}", invId);
                    return ResponseEntity.ok("OK");
                } else {
                    log.warn("Проверка платежа не удалась для заказа: {}", invId);
                    return ResponseEntity.badRequest().body("FAIL");
                }

            } catch (Exception e) {
                log.error("Ошибка обработки результата платежа: {}", e.getMessage());
                return ResponseEntity.badRequest().body("ERROR");
            }
        });
    }

    @Operation(summary = "Получить историю платежей",
            description = "Возвращает историю всех платежей текущего пользователя")
    @GetMapping("/history")
    public Mono<ResponseEntity<List<PaymentHistory>>> getPaymentHistory() {
        return paymentService.getPaymentHistory()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка получения истории платежей: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

}