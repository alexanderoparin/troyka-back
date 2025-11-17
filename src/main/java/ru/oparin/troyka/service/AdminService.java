package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple8;
import ru.oparin.troyka.model.dto.admin.AdminPaymentDTO;
import ru.oparin.troyka.model.dto.admin.AdminStatsDTO;
import ru.oparin.troyka.model.dto.admin.AdminUserDTO;
import ru.oparin.troyka.model.entity.Payment;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.model.entity.UserPoints;
import ru.oparin.troyka.model.enums.PaymentStatus;
import ru.oparin.troyka.repository.PaymentRepository;
import ru.oparin.troyka.repository.UserPointsRepository;
import ru.oparin.troyka.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static ru.oparin.troyka.config.DatabaseConfig.withRetry;

/**
 * Сервис для работы с админ-панелью.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final UserPointsRepository userPointsRepository;

    /**
     * Получить все платежи для админ-панели.
     */
    public Flux<AdminPaymentDTO> getAllPayments() {
        return paymentRepository.findAll()
                .flatMap(payment -> {
                    Mono<User> userMono = withRetry(userRepository.findById(payment.getUserId()))
                            .switchIfEmpty(Mono.just(User.builder()
                                    .username("Неизвестный")
                                    .email("")
                                    .telegramUsername("")
                                    .telegramFirstName("")
                                    .telegramPhotoUrl("")
                                    .build()));
                    
                    return userMono.map(user -> AdminPaymentDTO.fromPayment(payment, user));
                })
                .sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
    }

    /**
     * Получить всех пользователей для админ-панели.
     */
    public Flux<AdminUserDTO> getAllUsers() {
        return userRepository.findAll()
                .flatMap(user -> {
                    Mono<UserPoints> pointsMono = withRetry(userPointsRepository.findByUserId(user.getId()))
                            .switchIfEmpty(Mono.just(UserPoints.builder()
                                    .userId(user.getId())
                                    .points(0)
                                    .build()));
                    
                    return pointsMono.map(points -> AdminUserDTO.builder()
                            .id(user.getId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .role(user.getRole().name())
                            .emailVerified(user.getEmailVerified())
                            .telegramId(user.getTelegramId())
                            .telegramUsername(user.getTelegramUsername())
                            .telegramFirstName(user.getTelegramFirstName())
                            .telegramPhotoUrl(user.getTelegramPhotoUrl())
                            .points(points.getPoints())
                            .createdAt(user.getCreatedAt())
                            .updatedAt(user.getUpdatedAt())
                            .build());
                })
                .sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
    }

    /**
     * Получить статистику для админ-панели.
     */
    public Mono<AdminStatsDTO> getStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.with(LocalTime.MIN);
        LocalDateTime weekStart = now.minusDays(7).with(LocalTime.MIN);
        LocalDateTime monthStart = now.minusDays(30).with(LocalTime.MIN);

        Mono<Long> totalUsersMono = withRetry(userRepository.count());
        
        Mono<Long> totalPaymentsMono = withRetry(paymentRepository.count());
        
        Mono<BigDecimal> totalRevenueMono = calculateTotalRevenue();
        
        Mono<BigDecimal> todayRevenueMono = calculateRevenueSince(todayStart);
        
        Mono<BigDecimal> weekRevenueMono = calculateRevenueSince(weekStart);
        
        Mono<BigDecimal> monthRevenueMono = calculateRevenueSince(monthStart);
        
        Mono<Long> paidCountMono = paymentRepository.findByStatus(PaymentStatus.PAID).count();
        
        Mono<Long> pendingCountMono = paymentRepository.findByStatus(PaymentStatus.PENDING).count();
        
        Mono<Long> failedCountMono = paymentRepository.findByStatus(PaymentStatus.FAILED).count();
        
        Mono<BigDecimal> avgPaymentMono = calculateAveragePayment();

        Mono<Tuple8<Long, Long, BigDecimal, BigDecimal, BigDecimal, BigDecimal, Long, Long>> firstZip = 
                Mono.zip(totalUsersMono, totalPaymentsMono, totalRevenueMono, todayRevenueMono, 
                        weekRevenueMono, monthRevenueMono, paidCountMono, pendingCountMono);
        
        Mono<Tuple2<Long, BigDecimal>> secondZip = 
                Mono.zip(failedCountMono, avgPaymentMono);

        return Mono.zip(firstZip, secondZip)
                .map(tuple -> {
                    Tuple8<Long, Long, BigDecimal, BigDecimal, BigDecimal, BigDecimal, Long, Long> first = tuple.getT1();
                    Tuple2<Long, BigDecimal> second = tuple.getT2();
                    
                    return AdminStatsDTO.builder()
                            .totalUsers(first.getT1())
                            .totalPayments(first.getT2())
                            .totalRevenue(first.getT3())
                            .todayRevenue(first.getT4())
                            .weekRevenue(first.getT5())
                            .monthRevenue(first.getT6())
                            .paidPaymentsCount(first.getT7())
                            .pendingPaymentsCount(first.getT8())
                            .failedPaymentsCount(second.getT1())
                            .averagePaymentAmount(second.getT2())
                            .build();
                });
    }

    private Mono<BigDecimal> calculateTotalRevenue() {
        return paymentRepository.findByStatus(PaymentStatus.PAID)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .switchIfEmpty(Mono.just(BigDecimal.ZERO));
    }

    private Mono<BigDecimal> calculateRevenueSince(LocalDateTime since) {
        return paymentRepository.findByStatus(PaymentStatus.PAID)
                .filter(payment -> payment.getPaidAt() != null && payment.getPaidAt().isAfter(since))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .switchIfEmpty(Mono.just(BigDecimal.ZERO));
    }

    private Mono<BigDecimal> calculateAveragePayment() {
        return paymentRepository.findByStatus(PaymentStatus.PAID)
                .map(Payment::getAmount)
                .collectList()
                .map(amounts -> {
                    if (amounts.isEmpty()) {
                        return BigDecimal.ZERO;
                    }
                    BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                    return sum.divide(BigDecimal.valueOf(amounts.size()), 2, java.math.RoundingMode.HALF_UP);
                })
                .switchIfEmpty(Mono.just(BigDecimal.ZERO));
    }

}

