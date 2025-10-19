package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.payment.PaymentHistory;
import ru.oparin.troyka.model.dto.payment.PaymentRequest;
import ru.oparin.troyka.model.dto.payment.PaymentResponse;
import ru.oparin.troyka.model.entity.Payment;
import ru.oparin.troyka.repository.PaymentRepository;
import ru.oparin.troyka.repository.UserRepository;
import ru.oparin.troyka.util.SecurityUtil;

import java.util.List;

/**
 * Сервис для работы с платежами
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final RobokassaService robokassaService;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Создает платеж для текущего пользователя
     * 
     * @param request данные для создания платежа
     * @return ответ с URL для оплаты
     */
    public Mono<PaymentResponse> createPayment(PaymentRequest request) {
        log.info("Создание платежа по запросу: {}", request);
        return SecurityUtil.getCurrentUsername()
                .flatMap(userRepository::findByUsername)
                .map(user -> {
                    request.setUserId(user.getId());
                    return request;
                })
                .flatMap(robokassaService::createPayment)
                .doOnSuccess(response -> log.info("Платеж успешно создан: {}", response.getPaymentId()))
                .doOnError(error -> log.error("Ошибка создания платежа: {}", error.getMessage()));
    }

    /**
     * Получает историю платежей текущего пользователя
     * 
     * @return список платежей пользователя
     */
    public Mono<List<PaymentHistory>> getPaymentHistory() {
        return SecurityUtil.getCurrentUsername()
                .flatMap(userRepository::findByUsername)
                .flatMapMany(user -> paymentRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .map(this::mapToPaymentHistory)
                .collectList()
                .doOnError(error -> log.error("Ошибка получения истории платежей: {}", error.getMessage()));
    }

    /**
     * Маппит Payment entity в PaymentHistory DTO
     * 
     * @param payment сущность платежа
     * @return DTO для истории платежей
     */
    private PaymentHistory mapToPaymentHistory(Payment payment) {
        return PaymentHistory.builder()
                .id(payment.getId().intValue())
                .amount(payment.getAmount().doubleValue())
                .description(payment.getDescription())
                .status(payment.getStatus().name())
                .creditsAmount(payment.getCreditsAmount())
                .paidAt(payment.getPaidAt() != null ? payment.getPaidAt().toString() : null)
                .createdAt(payment.getCreatedAt().toString())
                .build();
    }
}
