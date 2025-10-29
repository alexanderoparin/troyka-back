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

    @Operation(summary = "Обработать результат платежа",
            description = "Callback эндпоинт для получения результата платежа от Робокассы. " +
                    "Проверяет подпись и обновляет статус платежа в системе. " +
                    "Возвращает ответ в формате OK[номер счета] согласно документации Робокассы.")
    @GetMapping("/result")
    @PostMapping("/result")
    public Mono<ResponseEntity<String>> handleResult(
            @RequestParam Map<String, String> allParams) {
        
        log.info("Получен результат платежа: {}", allParams);
        
        String outSum = allParams.get("OutSum");
        String invId = allParams.get("InvId");
        String signature = allParams.get("SignatureValue");

        // Проверяем наличие обязательных параметров
        if (outSum == null || invId == null || signature == null) {
            log.warn("Отсутствуют обязательные параметры в запросе от Робокассы");
            return Mono.just(ResponseEntity.ok("ERROR"));
        }

        return robokassaService.verifyPayment(outSum, invId, signature)
                .map(isValid -> {
                    if (isValid) {
                        // Согласно документации Робокассы: https://docs.robokassa.ru/pay-interface/#notification
                        // Обработчик должен вернуть ответ в формате "OK[номер счета]"
                        String response = "OK" + invId;
                        log.info("Платеж успешно проверен для заказа: {}. Возвращаем ответ: {}", invId, response);
                        return ResponseEntity.ok(response);
                    } else {
                        log.warn("Проверка платежа не удалась для заказа: {}", invId);
                        return ResponseEntity.ok("FAIL");
                    }
                })
                .onErrorResume(e -> {
                    log.error("Ошибка обработки результата платежа для заказа {}: {}", invId, e.getMessage(), e);
                    return Mono.just(ResponseEntity.ok("ERROR"));
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