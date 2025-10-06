package ru.oparin.troyka.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.Payment;
import ru.oparin.troyka.model.enums.PaymentStatus;

import java.time.LocalDateTime;

@Repository
public interface PaymentRepository extends ReactiveCrudRepository<Payment, Long> {

    Mono<Payment> findByOrderId(String orderId);

    Flux<Payment> findByUserId(Long userId);

    Flux<Payment> findByStatus(PaymentStatus status);

    @Query("SELECT * FROM troyka.payment WHERE user_id = :userId ORDER BY created_at DESC")
    Flux<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT * FROM troyka.payment WHERE status = :status AND created_at < :beforeDate")
    Flux<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime beforeDate);
}
