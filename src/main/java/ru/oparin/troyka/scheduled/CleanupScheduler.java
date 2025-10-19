package ru.oparin.troyka.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.oparin.troyka.model.enums.PaymentStatus;
import ru.oparin.troyka.repository.EmailVerificationTokenRepository;
import ru.oparin.troyka.repository.PasswordResetTokenRepository;
import ru.oparin.troyka.repository.PaymentRepository;

import java.time.LocalDateTime;

/**
 * Планировщик задач очистки системы
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupScheduler {

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Очистка протухших токенов каждый день в 02:00
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredTokens() {
        log.info("Очистка протухших токенов...");
        
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Очистка протухших токенов подтверждения email
            Long emailTokensDeleted = emailVerificationTokenRepository.deleteByExpiresAtBefore(now)
                    .block();
            
            // Очистка протухших токенов сброса пароля
            Long passwordTokensDeleted = passwordResetTokenRepository.deleteByExpiresAtBefore(now)
                    .block();
            
            log.info("Очистка токенов завершена. Удалено: {} токенов подтверждения email, {} токенов сброса пароля", 
                    emailTokensDeleted, passwordTokensDeleted);
                    
        } catch (Exception e) {
            log.error("Ошибка при очистке токенов", e);
        }
    }

    /**
     * Отмена просроченных платежей каждый час
     */
    @Scheduled(fixedRate = 3600000) // каждый час
    public void cancelExpiredPayments() {
        log.debug("Проверка просроченных платежей...");
        
        try {
            LocalDateTime expiredTime = LocalDateTime.now().minusHours(24);
            
            paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.CREATED, expiredTime)
                    .flatMap(payment -> {
                        payment.setStatus(PaymentStatus.CANCELLED);
                        return paymentRepository.save(payment);
                    })
                    .doOnNext(payment -> log.info("Отменен просроченный платеж: {}", payment.getId()))
                    .doOnError(error -> log.error("Ошибка при отмене просроченных платежей: {}", error.getMessage()))
                    .subscribe();
                    
        } catch (Exception e) {
            log.error("Ошибка при проверке просроченных платежей", e);
        }
    }
}
