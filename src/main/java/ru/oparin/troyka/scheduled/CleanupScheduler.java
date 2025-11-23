package ru.oparin.troyka.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.config.properties.FalAiProperties;
import ru.oparin.troyka.model.enums.PaymentStatus;
import ru.oparin.troyka.model.enums.QueueStatus;
import ru.oparin.troyka.repository.EmailVerificationTokenRepository;
import ru.oparin.troyka.repository.ImageGenerationHistoryRepository;
import ru.oparin.troyka.repository.PasswordResetTokenRepository;
import ru.oparin.troyka.repository.PaymentRepository;
import ru.oparin.troyka.service.FalAIHealthCheckService;
import ru.oparin.troyka.service.FalAIQueueService;
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
    private final FalAIHealthCheckService falAIHealthCheckService;
    private final FalAiProperties falAiProperties;
    private final FalAIQueueService falAIQueueService;
    private final ImageGenerationHistoryRepository imageGenerationHistoryRepository;

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

    /**
     * Проверка доступности FAL AI API.
     * Выполняется по расписанию каждые 30 минут.
     */
    @Scheduled(fixedRateString = "${fal.ai.health-check.interval-ms}")
    public void checkFalAIHealth() {
        FalAiProperties.HealthCheck healthCheck = falAiProperties.getHealthCheck();
        if (healthCheck == null || !healthCheck.isEnabled()) {
            log.debug("Проверка здоровья FAL AI отключена");
            return;
        }

        try {
            falAIHealthCheckService.checkFalAIHealth();
        } catch (Exception e) {
            log.error("Ошибка при проверке здоровья FAL AI", e);
        }
    }

    /**
     * Опрос статусов активных запросов в очереди Fal.ai.
     * Выполняется каждые 5 секунд (настраивается через fal.ai.queue.polling-interval-ms).
     */
    @Scheduled(fixedRateString = "${fal.ai.queue.polling-interval-ms}")
    public void pollFalAIQueueRequests() {
        if (falAiProperties.getQueue().isEnabled()) {
            try {
                imageGenerationHistoryRepository.findAll()
                        .filter(history -> QueueStatus.isActive(history.getQueueStatus()))
                        .flatMap(history ->
                                falAIQueueService.pollStatus(history)
                                        .onErrorResume(e -> {
                                            log.error("Ошибка при опросе статуса запроса {}", history.getId(), e);
                                            return Mono.just(history);
                                        }))
                        .doOnError(error -> log.error("Ошибка при опросе очереди Fal.ai", error))
                        .subscribe();
            } catch (Exception e) {
                log.error("Ошибка при запуске опроса очереди Fal.ai", e);
            }
        }
    }
}
