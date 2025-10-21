package ru.oparin.troyka.service.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.oparin.troyka.repository.TelegramBotSessionRepository;
import ru.oparin.troyka.repository.UserRepository;

import java.util.List;

/**
 * Сервис для отправки уведомлений в Telegram.
 * Отправляет уведомления о готовности изображений и другую информацию пользователям.
 */
@Service
@Slf4j
public class TelegramNotificationService extends DefaultAbsSender {

    private final UserRepository userRepository;
    private final TelegramBotSessionRepository telegramBotSessionRepository;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    public TelegramNotificationService(@Value("${telegram.bot.token}") String botToken,
                                       UserRepository userRepository,
                                       TelegramBotSessionRepository telegramBotSessionRepository) {
        super(new DefaultBotOptions());
        this.botToken = botToken;
        this.userRepository = userRepository;
        this.telegramBotSessionRepository = telegramBotSessionRepository;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    /**
     * Отправить уведомление о готовности изображений.
     *
     * @param userId ID пользователя
     * @param imageUrls список URL изображений
     * @param prompt промпт, по которому были созданы изображения
     */
    public Mono<Void> sendImageReadyNotification(Long userId, List<String> imageUrls, String prompt) {
        log.info("Отправка уведомления о готовности изображений пользователю {}", userId);

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Пользователь {} не найден для отправки уведомления", userId);
                    return Mono.empty();
                }))
                .flatMap(user -> {
                    if (user.getTelegramId() == null || !Boolean.TRUE.equals(user.getTelegramNotificationsEnabled())) {
                        log.debug("Пользователь {} не имеет Telegram ID или отключил уведомления", userId);
                        return Mono.empty();
                    }

                    return sendImageReadyMessage(user.getTelegramId(), imageUrls, prompt);
                })
                .doOnSuccess(v -> log.info("Уведомление о готовности изображений отправлено пользователю {}", userId))
                .doOnError(error -> log.error("Ошибка отправки уведомления пользователю {}", userId, error));
    }

    /**
     * Отправить уведомление о низком балансе поинтов.
     *
     * @param userId ID пользователя
     * @param currentPoints текущий баланс поинтов
     */
    public Mono<Void> sendLowBalanceNotification(Long userId, Integer currentPoints) {
        log.info("Отправка уведомления о низком балансе пользователю {}", userId);

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Пользователь {} не найден для отправки уведомления о балансе", userId);
                    return Mono.empty();
                }))
                .flatMap(user -> {
                    if (user.getTelegramId() == null || !Boolean.TRUE.equals(user.getTelegramNotificationsEnabled())) {
                        log.debug("Пользователь {} не имеет Telegram ID или отключил уведомления", userId);
                        return Mono.empty();
                    }

                    return sendLowBalanceMessage(user.getTelegramId(), currentPoints);
                })
                .doOnSuccess(v -> log.info("Уведомление о низком балансе отправлено пользователю {}", userId))
                .doOnError(error -> log.error("Ошибка отправки уведомления о балансе пользователю {}", userId, error));
    }

    /**
     * Отправить приветственное сообщение новому пользователю.
     *
     * @param userId ID пользователя
     */
    public Mono<Void> sendWelcomeMessage(Long userId) {
        log.info("Отправка приветственного сообщения пользователю {}", userId);

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Пользователь {} не найден для отправки приветствия", userId);
                    return Mono.empty();
                }))
                .flatMap(user -> {
                    if (user.getTelegramId() == null) {
                        log.debug("Пользователь {} не имеет Telegram ID", userId);
                        return Mono.empty();
                    }

                    return sendWelcomeMessage(user.getTelegramId(), user.getUsername());
                })
                .doOnSuccess(v -> log.info("Приветственное сообщение отправлено пользователю {}", userId))
                .doOnError(error -> log.error("Ошибка отправки приветственного сообщения пользователю {}", userId, error));
    }

    /**
     * Отправить сообщение о готовности изображений.
     */
    private Mono<Void> sendImageReadyMessage(Long telegramId, List<String> imageUrls, String prompt) {
        String message = String.format(
                "🎨 *Изображения готовы!*\n\n" +
                "📝 *Промпт:* %s\n" +
                "🖼️ *Создано изображений:* %d\n\n" +
                "Ваши изображения:",
                prompt,
                imageUrls.size()
        );

        return sendMessage(telegramId, message)
                .then(sendImages(telegramId, imageUrls))
                .then();
    }

    /**
     * Отправить сообщение о низком балансе.
     */
    private Mono<Void> sendLowBalanceMessage(Long telegramId, Integer currentPoints) {
        String message = String.format(
                "⚠️ *Низкий баланс поинтов*\n\n" +
                "💰 *Текущий баланс:* %d поинтов\n" +
                "🛒 *Рекомендуем пополнить баланс* для продолжения генерации изображений.\n\n" +
                "Перейдите на сайт: https://24reshai.ru/pricing",
                currentPoints
        );

        return sendMessage(telegramId, message).then();
    }

    /**
     * Отправить приветственное сообщение.
     */
    private Mono<Void> sendWelcomeMessage(Long telegramId, String username) {
        String message = String.format(
                "👋 *Добро пожаловать в 24reshai, %s!*\n\n" +
                "🎨 Теперь вы можете генерировать изображения прямо из Telegram!\n\n" +
                "📝 *Как использовать:*\n" +
                "• Отправьте текстовое описание для генерации\n" +
                "• Приложите фото + описание для генерации с референсом\n" +
                "• Используйте команды: /help, /balance, /history\n\n" +
                "🚀 *Начните с отправки описания вашего изображения!*",
                username
        );

        return sendMessage(telegramId, message).then();
    }

    /**
     * Отправить изображения пользователю.
     */
    private Mono<Void> sendImages(Long telegramId, List<String> imageUrls) {
        return Mono.fromRunnable(() -> {
            try {
                for (String imageUrl : imageUrls) {
                    // Здесь будет логика отправки изображений через Telegram Bot API
                    // Пока что просто логируем
                    log.info("Отправка изображения {} пользователю {}", imageUrl, telegramId);
                }
            } catch (Exception e) {
                log.error("Ошибка отправки изображений пользователю {}", telegramId, e);
            }
        });
    }

    /**
     * Отправить сообщение пользователю.
     */
    private Mono<Boolean> sendMessage(Long chatId, String text) {
        return Mono.fromCallable(() -> {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            message.setParseMode("Markdown");
            try {
                execute(message);
                log.info("Сообщение успешно отправлено в Telegram чат {}: {}", chatId, text);
                return true;
            } catch (TelegramApiException e) {
                log.error("Ошибка при отправке сообщения в Telegram чат {}: {}", chatId, e.getMessage());
                return false;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
