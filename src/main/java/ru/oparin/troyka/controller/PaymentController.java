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
import ru.oparin.troyka.model.dto.payment.PaymentRequest;
import ru.oparin.troyka.model.dto.payment.PaymentResponse;
import ru.oparin.troyka.repository.UserRepository;
import ru.oparin.troyka.service.RobokassaService;
import ru.oparin.troyka.util.SecurityUtil;

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

    private final RobokassaService robokassaService;
    private final UserRepository userRepository;

    @Operation(summary = "Создать платеж",
            description = "Создает новый платеж в системе Робокасса и возвращает URL для оплаты")
    @PostMapping("/create")
    public Mono<ResponseEntity<PaymentResponse>> createPayment(
            @Parameter(description = "Данные для создания платежа", required = true)
            @Valid @RequestBody PaymentRequest request) {
        return SecurityUtil.getCurrentUsername()
                .flatMap(userRepository::findByUsername)
                .map(user -> {
                    request.setUserId(user.getId());
                    return request;
                })
                .map(robokassaService::createPayment)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка создания платежа: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    @Operation(summary = "Обработать результат платежа",
            description = "Callback эндпоинт для получения результата платежа от Робокассы. " +
                    "Проверяет подпись и обновляет статус платежа в системе.")
    @PostMapping("/result")
    public ResponseEntity<String> handleResult(
            @Parameter(description = "Параметры от Робокассы (OutSum, InvId, SignatureValue)", required = true)
            @RequestParam Map<String, String> params) {
        try {
            String outSum = params.get("OutSum");
            String invId = params.get("InvId");
            String signature = params.get("SignatureValue");

            log.info("Получен результат платежа: OutSum={}, InvId={}, Signature={}", outSum, invId, signature);

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
    }
}