package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.payment.PaymentHistory;
import ru.oparin.troyka.model.dto.payment.PaymentRequest;
import ru.oparin.troyka.model.dto.payment.PaymentResponse;
import ru.oparin.troyka.model.entity.Payment;
import ru.oparin.troyka.model.enums.PaymentStatus;
import ru.oparin.troyka.repository.PaymentRepository;
import ru.oparin.troyka.repository.UserRepository;
import ru.oparin.troyka.util.SecurityUtil;

import java.time.LocalDateTime;
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
     * Автоматически отменяет просроченные платежи (запуск каждый час)
     * Платежи в статусе CREATED старше 24 часов отменяются
     */
    @Scheduled(fixedRate = 3600000) // каждый час
    public void cancelExpiredPayments() {
        LocalDateTime expiredTime = LocalDateTime.now().minusHours(24);
        
        paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.CREATED, expiredTime)
                .flatMap(payment -> {
                    payment.setStatus(PaymentStatus.CANCELLED);
                    return paymentRepository.save(payment);
                })
                .doOnNext(payment -> log.info("Автоматически отменен просроченный платеж: {}", payment.getId()))
                .doOnError(error -> log.error("Ошибка при отмене просроченных платежей: {}", error.getMessage()))
                .subscribe();
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
