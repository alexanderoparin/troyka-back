package ru.oparin.troyka.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.oparin.troyka.model.enums.PaymentStatus;
import ru.oparin.troyka.repository.EmailVerificationTokenRepository;
import ru.oparin.troyka.repository.PasswordResetTokenRepository;
import ru.oparin.troyka.repository.PaymentRepository;
import ru.oparin.troyka.service.FileCleanupService;

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
    private final FileCleanupService fileCleanupService;

    /**
     * Очистка протухших токенов каждый день в 02:00
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredTokens() {
        log.info("Очистка протухших токенов...");

        try {
            LocalDateTime now = LocalDateTime.now();

            // Очистка протухших токенов подтверждения email
            Long emailTokensDeleted = emailVerificationTokenRepository.deleteByExpiresAtBefore(now).block();

            // Очистка протухших токенов сброса пароля
            Long passwordTokensDeleted = passwordResetTokenRepository.deleteByExpiresAtBefore(now).block();

            log.info("Очистка токенов завершена. Удалено: {} токенов подтверждения email, {} токенов сброса пароля",
                    emailTokensDeleted, passwordTokensDeleted);

        } catch (Exception e) {
            log.error("Ошибка при очистке токенов", e);
        }
    }

    /**
     * Отмена просроченных платежей каждый день в 01:00
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void cancelExpiredPayments() {
        log.debug("Поиск просроченных платежей по расписанию");
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

    /**
     * Очистка старых неиспользуемых файлов каждый день в 03:00
     * Удаляет файлы старше 30 дней, которые не используются в БД
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldUnusedFiles() {
        log.info("Очистка старых неиспользуемых файлов...");
        try {
            int daysOld = 30; // Удаляем файлы старше 30 дней
            fileCleanupService.cleanupOldUnusedFiles(daysOld)
                    .doOnNext(count -> log.info("Очистка файлов завершена. Удалено файлов: {}", count))
                    .doOnError(error -> log.error("Ошибка при очистке файлов: {}", error.getMessage()))
                    .subscribe();
        } catch (Exception e) {
            log.error("Ошибка при очистке старых файлов", e);
        }
    }
}
