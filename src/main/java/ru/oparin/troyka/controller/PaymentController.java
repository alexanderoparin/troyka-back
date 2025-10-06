package ru.oparin.troyka.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.oparin.troyka.model.dto.payment.PaymentRequest;
import ru.oparin.troyka.model.dto.payment.PaymentResponse;
import ru.oparin.troyka.service.RobokassaService;

import java.util.Map;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final RobokassaService robokassaService;

    @PostMapping("/create")
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest request) {
        try {
            PaymentResponse response = robokassaService.createPayment(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error creating payment: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/result")
    public ResponseEntity<String> handleResult(@RequestParam Map<String, String> params) {
        try {
            String outSum = params.get("OutSum");
            String invId = params.get("InvId");
            String signature = params.get("SignatureValue");

            log.info("Received payment result: OutSum={}, InvId={}, Signature={}", outSum, invId, signature);

            if (robokassaService.verifyPayment(outSum, invId, signature)) {
                // Здесь можно обновить статус заказа в базе данных
                // Например, добавить поинты пользователю
                log.info("Payment verified successfully for order: {}", invId);
                return ResponseEntity.ok("OK");
            } else {
                log.warn("Payment verification failed for order: {}", invId);
                return ResponseEntity.badRequest().body("FAIL");
            }

        } catch (Exception e) {
            log.error("Error processing payment result: {}", e.getMessage());
            return ResponseEntity.badRequest().body("ERROR");
        }
    }
}