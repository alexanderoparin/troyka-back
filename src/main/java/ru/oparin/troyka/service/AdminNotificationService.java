package ru.oparin.troyka.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.model.enums.Role;
import ru.oparin.troyka.service.telegram.TelegramBotSessionService;
import ru.oparin.troyka.service.telegram.TelegramMessageService;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Сервис для отправки уведомлений администраторам системы.
 * Используется для критических уведомлений, таких как проблемы с внешними сервисами.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNotificationService {

    private final JavaMailSender mailSender;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final TelegramMessageService telegramMessageService;
    private final TelegramBotSessionService telegramBotSessionService;

    @Value("${app.email.from:noreply@24reshai.ru}")
    private String fromEmail;

    // Кэш для отслеживания последней отправки уведомлений (ключ: тип уведомления, значение: время последней отправки)
    private final Cache<String, LocalDateTime> lastNotificationTime = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(100)
            .build();

    private static final String FAL_BALANCE_EXHAUSTED_KEY = "fal_balance_exhausted";
    private static final long NOTIFICATION_COOLDOWN_HOURS = 1;

    /**
     * Уведомить администраторов о проблеме с балансом Fal.ai.
     * Отправляет письмо всем администраторам, но не чаще одного раза в час.
     *
     * @param errorMessage сообщение об ошибке от Fal.ai
     */
    public Mono<Void> notifyAdminsAboutFalBalance(String errorMessage) {
        return Mono.fromCallable(() -> {
            // Проверяем, не отправляли ли мы уведомление недавно
            LocalDateTime lastSent = lastNotificationTime.getIfPresent(FAL_BALANCE_EXHAUSTED_KEY);
            if (lastSent != null) {
                LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(NOTIFICATION_COOLDOWN_HOURS);
                if (lastSent.isAfter(oneHourAgo)) {
                    log.debug("Уведомление о балансе Fal.ai уже было отправлено {} минут назад, пропускаем",
                            Duration.between(lastSent, LocalDateTime.now()).toMinutes());
                    return false;
                }
            }
            return true;
        })
        .flatMap(shouldSend -> {
            if (!shouldSend) {
                return Mono.empty();
            }

            // Получаем список всех администраторов
            return getAdminUsers()
                    .collectList()
                    .flatMap(admins -> {
                        if (admins.isEmpty()) {
                            log.warn("Не найдено администраторов для отправки уведомления о балансе Fal.ai");
                            return Mono.empty();
                        }

                        // Отправляем письмо и Telegram уведомление каждому администратору
                        // Email отправляется только тем, у кого заполнен email
                        // Telegram отправляется только тем, у кого есть telegramId
                        return Flux.fromIterable(admins)
                                .flatMap(admin -> {
                                    Mono<Void> emailMono = (admin.getEmail() != null && !admin.getEmail().trim().isEmpty())
                                            ? sendBalanceNotificationEmail(admin, errorMessage)
                                            : Mono.empty();
                                    Mono<Void> telegramMono = sendBalanceNotificationTelegram(admin, errorMessage);
                                    return Mono.when(emailMono, telegramMono);
                                })
                                .then(Mono.fromRunnable(() -> {
                                    // Сохраняем время отправки в кэш
                                    lastNotificationTime.put(FAL_BALANCE_EXHAUSTED_KEY, LocalDateTime.now());
                                    log.info("Уведомление о балансе Fal.ai отправлено {} администраторам (email и/или Telegram)", admins.size());
                                }));
                    });
        })
        .then();
    }

    /**
     * Получить список всех администраторов системы.
     *
     * @return Flux с пользователями, имеющими роль ADMIN
     */
    private Flux<User> getAdminUsers() {
        return r2dbcEntityTemplate.select(
                Query.query(Criteria.where("role").is(Role.ADMIN.name())),
                User.class
        );
    }

    /**
     * Отправить письмо администратору о проблеме с балансом Fal.ai.
     *
     * @param admin администратор, которому отправляется письмо
     * @param errorMessage сообщение об ошибке от Fal.ai
     */
    private Mono<Void> sendBalanceNotificationEmail(User admin, String errorMessage) {
        return Mono.fromRunnable(() -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromEmail);
                message.setTo(admin.getEmail());
                message.setSubject("⚠️ Критическое уведомление: Исчерпан баланс Fal.ai");
                message.setText(buildEmailContent(errorMessage));

                mailSender.send(message);
                log.info("Уведомление о балансе Fal.ai отправлено администратору: {}", admin.getEmail());
            } catch (Exception e) {
                log.error("Ошибка при отправке уведомления администратору {}: {}", admin.getEmail(), e.getMessage(), e);
            }
        });
    }

    /**
     * Отправить уведомление администратору в Telegram о проблеме с балансом Fal.ai.
     *
     * @param admin администратор, которому отправляется уведомление
     * @param errorMessage сообщение об ошибке от Fal.ai
     */
    private Mono<Void> sendBalanceNotificationTelegram(User admin, String errorMessage) {
        // Проверяем, есть ли у админа telegramId
        if (admin.getTelegramId() == null) {
            log.debug("У администратора {} нет telegramId, пропускаем отправку Telegram уведомления", admin.getUsername());
            return Mono.empty();
        }

        // Получаем chatId из TelegramBotSession
        // Отправляем только если админ уже начинал диалог с ботом (есть сессия)
        // Это гарантирует, что бот может отправить сообщение (не будет ошибки "bot can't initiate conversation")
        return telegramBotSessionService.getTelegramBotSessionEntityByUserId(admin.getId())
                .flatMap(telegramBotSession -> {
                    Long chatId = telegramBotSession.getChatId();
                    String telegramMessage = buildTelegramMessage(errorMessage);
                    return telegramMessageService.sendMessage(chatId, telegramMessage)
                            .doOnSuccess(v -> log.info("Telegram уведомление о балансе Fal.ai отправлено администратору {} (chatId: {})", 
                                    admin.getUsername(), chatId))
                            .onErrorResume(e -> {
                                log.warn("Ошибка при отправке Telegram уведомления администратору {} (chatId: {}): {}", 
                                        admin.getUsername(), chatId, e.getMessage());
                                return Mono.empty(); // Не блокируем отправку email при ошибке Telegram
                            });
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("У администратора {} нет активной сессии Telegram бота, пропускаем отправку Telegram уведомления. " +
                            "Админ должен сначала написать боту /start", admin.getUsername());
                    return Mono.empty();
                }))
                .onErrorResume(e -> {
                    log.warn("Ошибка при получении Telegram сессии для администратора {}: {}", admin.getUsername(), e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Построить сообщение для Telegram с уведомлением о балансе.
     *
     * @param errorMessage сообщение об ошибке от Fal.ai
     * @return текст сообщения для Telegram
     */
    private String buildTelegramMessage(String errorMessage) {
        return String.format(
                "⚠️ *Критическое уведомление: Исчерпан баланс Fal.ai*\n\n" +
                "Обнаружена критическая проблема с балансом сервиса Fal.ai.\n\n" +
                "*Детали ошибки:*\n`%s`\n\n" +
                "Необходимо пополнить баланс на [fal.ai/dashboard/billing](https://fal.ai/dashboard/billing)\n\n" +
                "До пополнения баланса генерация изображений будет недоступна для пользователей.\n\n" +
                "_Это автоматическое уведомление. Повторное уведомление будет отправлено через час, если проблема сохранится._",
                errorMessage.replace("_", "\\_").replace("*", "\\*").replace("[", "\\[").replace("]", "\\]")
        );
    }

    /**
     * Построить содержимое письма с уведомлением о балансе.
     *
     * @param errorMessage сообщение об ошибке от Fal.ai
     * @return текст письма
     */
    private String buildEmailContent(String errorMessage) {
        return String.format(
                "Здравствуйте!\n\n" +
                "Обнаружена критическая проблема с балансом сервиса Fal.ai.\n\n" +
                "Детали ошибки:\n%s\n\n" +
                "Необходимо пополнить баланс на https://fal.ai/dashboard/billing\n\n" +
                "До пополнения баланса генерация изображений будет недоступна для пользователей.\n\n" +
                "Это автоматическое уведомление. Повторное уведомление будет отправлено через час, если проблема сохранится.\n\n" +
                "---\n" +
                "Система 24reshai.ru",
                errorMessage
        );
    }
}

