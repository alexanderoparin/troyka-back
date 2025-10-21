package ru.oparin.troyka.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.repository.UserRepository;
import ru.oparin.troyka.service.FalAIService;
import ru.oparin.troyka.service.UserPointsService;

import java.util.List;

/**
 * Основной сервис для работы с Telegram ботом.
 * Обрабатывает команды и сообщения от пользователей.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class TelegramBotService {

    private final UserRepository userRepository;
    private final TelegramBotSessionService telegramBotSessionService;
    private final UserPointsService userPointsService;
    private final FalAIService falAIService;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    /**
     * Обработать команду /start.
     *
     * @param chatId ID чата
     * @param telegramId ID пользователя в Telegram
     * @param username имя пользователя в Telegram
     */
    public Mono<Void> handleStartCommand(Long chatId, Long telegramId, String username) {
        log.info("Обработка команды /start для чата {} и пользователя {}", chatId, telegramId);

        return userRepository.findByTelegramId(telegramId)
                .flatMap(user -> {
                    // Пользователь уже зарегистрирован
                    return sendMessage(chatId, String.format(
                            "👋 *Добро пожаловать обратно, %s!*\n\n" +
                            "🎨 Ваш аккаунт уже привязан к Telegram.\n" +
                            "Вы можете генерировать изображения прямо здесь!\n\n" +
                            "📝 *Как использовать:*\n" +
                            "• Отправьте текстовое описание\n" +
                            "• Приложите фото + описание\n" +
                            "• Используйте /help для справки",
                            user.getUsername()
                    ));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Новый пользователь - создаем аккаунт
                    return createUserFromTelegram(telegramId, username)
                            .flatMap(user -> userRepository.save(user)
                                    .flatMap(savedUser -> userPointsService.addPointsToUser(savedUser.getId(), 6)
                                            .then(telegramBotSessionService.getOrCreateTelegramBotSession(savedUser.getId(), chatId))
                                            .then(sendWelcomeMessage(chatId, savedUser.getUsername()))));
                }))
                .doOnSuccess(v -> log.info("Команда /start обработана для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки команды /start для чата {}", chatId, error));
    }

    /**
     * Обработать команду /help.
     *
     * @param chatId ID чата
     */
    public Mono<Void> handleHelpCommand(Long chatId) {
        log.info("Обработка команды /help для чата {}", chatId);

        String helpMessage = "🤖 *Справка по боту 24reshai*\n\n" +
                "📝 *Основные команды:*\n" +
                "• /start - Начать работу с ботом\n" +
                "• /help - Показать эту справку\n" +
                "• /balance - Проверить баланс поинтов\n" +
                "• /history - История генераций\n" +
                "• /settings - Настройки уведомлений\n\n" +
                "🎨 *Генерация изображений:*\n" +
                "• Отправьте текстовое описание\n" +
                "• Приложите фото + описание для генерации с референсом\n" +
                "• Каждая генерация стоит 3 поинта\n\n" +
                "💡 *Советы:*\n" +
                "• Будьте конкретны в описаниях\n" +
                "• Используйте качественные референсы\n" +
                "• Результат готов за 5-10 секунд\n\n" +
                "🌐 *Сайт:* https://24reshai.ru";

        return sendMessage(chatId, helpMessage)
                .doOnSuccess(v -> log.info("Команда /help обработана для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки команды /help для чата {}", chatId, error));
    }

    /**
     * Обработать команду /balance.
     *
     * @param chatId ID чата
     * @param telegramId ID пользователя в Telegram
     */
    public Mono<Void> handleBalanceCommand(Long chatId, Long telegramId) {
        log.info("Обработка команды /balance для чата {} и пользователя {}", chatId, telegramId);

        return userRepository.findByTelegramId(telegramId)
                .switchIfEmpty(Mono.defer(() -> {
                    return sendMessage(chatId, "❌ Пользователь не найден. Используйте /start для регистрации.")
                            .then(Mono.empty());
                }))
                .flatMap(user -> userPointsService.getUserPoints(user.getId())
                        .map(points -> String.format(
                                "💰 *Ваш баланс поинтов*\n\n" +
                                "🔢 *Текущий баланс:* %d поинтов\n" +
                                "🎨 *Доступно генераций:* %d\n\n" +
                                "💳 *Пополнить баланс:* https://24reshai.ru/pricing",
                                points,
                                points / 3
                        ))
                        .flatMap(message -> sendMessage(chatId, message)))
                .doOnSuccess(v -> log.info("Команда /balance обработана для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки команды /balance для чата {}", chatId, error));
    }

    /**
     * Обработать команду /history.
     *
     * @param chatId ID чата
     * @param telegramId ID пользователя в Telegram
     */
    public Mono<Void> handleHistoryCommand(Long chatId, Long telegramId) {
        log.info("Обработка команды /history для чата {} и пользователя {}", chatId, telegramId);

        return userRepository.findByTelegramId(telegramId)
                .switchIfEmpty(Mono.defer(() -> {
                    return sendMessage(chatId, "❌ Пользователь не найден. Используйте /start для регистрации.")
                            .then(Mono.empty());
                }))
                .flatMap(user -> {
                    // Здесь будет логика получения истории генераций
                    // Пока что отправляем заглушку
                    return sendMessage(chatId, "📚 *История генераций*\n\n" +
                            "🔄 Функция в разработке...\n" +
                            "Пока что вы можете посмотреть историю на сайте: https://24reshai.ru/history");
                })
                .doOnSuccess(v -> log.info("Команда /history обработана для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки команды /history для чата {}", chatId, error));
    }

    /**
     * Обработать текстовое сообщение (промпт для генерации).
     *
     * @param chatId ID чата
     * @param telegramId ID пользователя в Telegram
     * @param prompt промпт для генерации
     */
    public Mono<Void> handleTextMessage(Long chatId, Long telegramId, String prompt) {
        log.info("Обработка текстового сообщения для чата {} и пользователя {}: {}", chatId, telegramId, prompt);

        return userRepository.findByTelegramId(telegramId)
                .switchIfEmpty(Mono.defer(() -> {
                    return sendMessage(chatId, "❌ Пользователь не найден. Используйте /start для регистрации.")
                            .then(Mono.empty());
                }))
                .flatMap(user -> {
                    // Проверяем баланс
                    return userPointsService.getUserPoints(user.getId())
                            .flatMap(points -> {
                                if (points < 3) {
                                    return sendMessage(chatId, "❌ *Недостаточно поинтов*\n\n" +
                                            "💰 *Текущий баланс:* " + points + " поинтов\n" +
                                            "🎨 *Требуется:* 3 поинта для генерации\n\n" +
                                            "💳 *Пополнить баланс:* https://24reshai.ru/pricing");
                                }

                                // Получаем специальную сессию
                                return telegramBotSessionService.getOrCreateTelegramBotSession(user.getId(), chatId)
                                        .flatMap(session -> {
                                            // Отправляем сообщение о начале генерации
                                            return sendMessage(chatId, "🎨 *Генерация изображения...*\n\n" +
                                                    "📝 *Промпт:* " + prompt + "\n" +
                                                    "⏱️ *Ожидайте 5-10 секунд*")
                                                    .then(generateImage(user.getId(), session.getId(), prompt, List.of()));
                                        });
                            });
                })
                .doOnSuccess(v -> log.info("Текстовое сообщение обработано для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки текстового сообщения для чата {}", chatId, error));
    }

    /**
     * Обработать фото с описанием.
     *
     * @param chatId ID чата
     * @param telegramId ID пользователя в Telegram
     * @param photoUrl URL фото
     * @param caption описание фото
     */
    public Mono<Void> handlePhotoMessage(Long chatId, Long telegramId, String photoUrl, String caption) {
        log.info("Обработка фото для чата {} и пользователя {}: {}", chatId, telegramId, caption);

        return userRepository.findByTelegramId(telegramId)
                .switchIfEmpty(Mono.defer(() -> {
                    return sendMessage(chatId, "❌ Пользователь не найден. Используйте /start для регистрации.")
                            .then(Mono.empty());
                }))
                .flatMap(user -> {
                    // Проверяем баланс
                    return userPointsService.getUserPoints(user.getId())
                            .flatMap(points -> {
                                if (points < 3) {
                                    return sendMessage(chatId, "❌ *Недостаточно поинтов*\n\n" +
                                            "💰 *Текущий баланс:* " + points + " поинтов\n" +
                                            "🎨 *Требуется:* 3 поинта для генерации\n\n" +
                                            "💳 *Пополнить баланс:* https://24reshai.ru/pricing");
                                }

                                // Получаем специальную сессию
                                return telegramBotSessionService.getOrCreateTelegramBotSession(user.getId(), chatId)
                                        .flatMap(session -> {
                                            // Отправляем сообщение о начале генерации
                                            return sendMessage(chatId, "🎨 *Генерация изображения с референсом...*\n\n" +
                                                    "📝 *Промпт:* " + caption + "\n" +
                                                    "🖼️ *Референс:* загружен\n" +
                                                    "⏱️ *Ожидайте 5-10 секунд*")
                                                    .then(generateImage(user.getId(), session.getId(), caption, List.of(photoUrl)));
                                        });
                            });
                })
                .doOnSuccess(v -> log.info("Фото обработано для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки фото для чата {}", chatId, error));
    }

    /**
     * Создать пользователя из данных Telegram.
     */
    private Mono<User> createUserFromTelegram(Long telegramId, String username) {
        return Mono.fromCallable(() -> {
            String email = "telegram_" + telegramId + "@telegram.local";
            String generatedUsername = username != null ? username : "tg_" + telegramId;

            return User.builder()
                    .username(generatedUsername)
                    .email(email)
                    .password("telegram_auth_" + telegramId) // Временный пароль
                    .emailVerified(true) // Telegram пользователи считаются верифицированными
                    .telegramId(telegramId)
                    .telegramUsername(username)
                    .telegramFirstName(username)
                    .telegramNotificationsEnabled(true)
                    .build();
        });
    }

    /**
     * Генерировать изображение.
     */
    private Mono<Void> generateImage(Long userId, Long sessionId, String prompt, List<String> inputImageUrls) {
        // Здесь будет интеграция с FalAIService
        // Пока что отправляем заглушку
        return Mono.fromRunnable(() -> {
            log.info("Генерация изображения для пользователя {} в сессии {} с промптом: {}", userId, sessionId, prompt);
            // TODO: Интеграция с FalAIService
        });
    }

    /**
     * Отправить приветственное сообщение.
     */
    private Mono<Void> sendWelcomeMessage(Long chatId, String username) {
        String message = String.format(
                "🎉 *Добро пожаловать в 24reshai, %s!*\n\n" +
                "🎨 Вы получили 6 поинтов при регистрации!\n" +
                "🚀 Теперь можете генерировать изображения прямо здесь!\n\n" +
                "📝 *Как начать:*\n" +
                "• Отправьте описание изображения\n" +
                "• Или приложите фото + описание\n\n" +
                "💡 Используйте /help для справки",
                username
        );

        return sendMessage(chatId, message);
    }

    /**
     * Отправить текстовое сообщение.
     */
    public Mono<Void> sendMessage(Long chatId, String message) {
        // Здесь будет логика отправки сообщений через Telegram Bot API
        // Пока что просто логируем
        log.info("Отправка сообщения в чат {}: {}", chatId, message);
        return Mono.empty();
    }
}
