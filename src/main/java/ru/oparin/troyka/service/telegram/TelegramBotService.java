package ru.oparin.troyka.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.dto.telegram.TelegramMessage;
import ru.oparin.troyka.model.dto.telegram.TelegramPhoto;
import ru.oparin.troyka.model.dto.telegram.TelegramUpdate;
import ru.oparin.troyka.model.entity.ArtStyle;
import ru.oparin.troyka.model.entity.TelegramBotSession;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.model.entity.UserStyle;
import ru.oparin.troyka.repository.UserRepository;
import ru.oparin.troyka.service.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Основной сервис для работы с Telegram ботом.
 * Обрабатывает команды и сообщения от пользователей.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class TelegramBotService {

    private final UserRepository userRepository;
    private final ArtStyleService artStyleService;
    private final TelegramBotSessionService telegramBotSessionService;
    private final UserPointsService userPointsService;
    private final FalAIService falAIService;
    private final TelegramMessageService telegramMessageService;
    private final ImageGenerationHistoryService imageGenerationHistoryService;
    private final GenerationProperties generationProperties;
    private final PromptEnhancementService promptEnhancementService;
    
    // Временное хранилище для списка стилей во время выбора
    private final Map<Long, List<ArtStyle>> sessionStyles = new HashMap<>();

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    /**
     * Обработать команду /start.
     *
     * @param chatId     ID чата
     * @param telegramId ID пользователя в Telegram
     * @param username   имя пользователя в Telegram
     */
    public Mono<Void> handleStartCommand(Long chatId, Long telegramId, String username, String firstName, String lastName) {
        log.info("Обработка команды /start для чата {} и пользователя {}", chatId, telegramId);

        Mono<User> userMono = userRepository.findByTelegramId(telegramId)
                .switchIfEmpty(Mono.defer(() -> {
                    // Новый пользователь - создаем аккаунт
                    return createUserFromTelegram(telegramId, username, firstName, lastName)
                            .flatMap(user -> userRepository.save(user)
                                    .flatMap(savedUser -> userPointsService.addPointsToUser(savedUser.getId(), generationProperties.getPointsOnRegistration())
                                            .then(telegramBotSessionService.getOrCreateTelegramBotSession(savedUser.getId(), chatId))
                                            .thenReturn(savedUser))
                            );
                }));
        
        return userMono
                .flatMap(user -> {
                    // Всегда отправляем приветственное сообщение для существующих пользователей
                    return sendMessage(chatId, String.format(
                            """
                                    👋 *Добро пожаловать обратно, %s!*
                                    
                                    🎨 Ваш аккаунт уже привязан к Telegram.
                                    Вы можете генерировать изображения прямо здесь!
                                    
                                    📝 *Как использовать:*
                                    • Отправьте текстовое описание
                                    • Приложите фото + описание
                                    
                                    💰 *Стоимость:* %s поинта за 1 изображение
                                    • Используйте /help для справки
                                    """, user.getUsername(), generationProperties.getPointsPerImage()
                    ));
                })
                .then()
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

        String helpMessage = String.format("""
                🤖 *Справка по боту 24reshai*
                
                📝 *Основные команды:*
                • /start - Начать работу с ботом
                • /help - Показать эту справку
                • /balance - Проверить баланс поинтов
                
                🎨 *Генерация изображений:*
                • Отправьте текстовое описание
                • Или приложите фото с подписью
                • Каждая генерация стоит %s поинтов
                • Результат готов за 5-10 секунд
                
                💡 *Советы:*
                • Чем подробнее описание, тем лучше результат
                • Используйте качественные референсы
                
                🌐 *Сайт:* https://24reshai.ru
                """, generationProperties.getPointsPerImage());

        return sendMessage(chatId, helpMessage)
                .doOnSuccess(v -> log.info("Команда /help обработана для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки команды /help для чата {}", chatId, error));
    }

    /**
     * Обработать команду /balance.
     *
     * @param chatId     ID чата
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
                                """
                                        💰 *Ваш баланс поинтов*
                                        
                                        🔢 *Текущий баланс:* %d поинтов
                                        🎨 *Доступно генераций:* %d
                                        
                                        💳 *Пополнить баланс:* https://24reshai.ru/pricing
                                        """, points, points / generationProperties.getPointsPerImage()
                        ))
                        .flatMap(message -> sendMessage(chatId, message)))
                .doOnSuccess(v -> log.info("Команда /balance обработана для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки команды /balance для чата {}", chatId, error));
    }

    /**
     * Обработать текстовое сообщение (промпт для генерации).
     *
     * @param chatId     ID чата
     * @param telegramId ID пользователя в Telegram
     * @param prompt     промпт для генерации
     */
    public Mono<Void> handleTextMessage(Long chatId, Long telegramId, String prompt) {
        return handleTextMessage(chatId, telegramId, prompt, List.of());
    }

    /**
     * Обработать текстовое сообщение с входными изображениями.
     *
     * @param chatId         ID чата
     * @param telegramId     ID пользователя в Telegram
     * @param prompt         промпт для генерации
     * @param inputImageUrls список URL входных изображений
     */
    public Mono<Void> handleTextMessage(Long chatId, Long telegramId, String prompt, List<String> inputImageUrls) {
        log.info("Обработка текстового сообщения для чата {} и пользователя {}: {} (входных изображений: {})",
                chatId, telegramId, prompt, inputImageUrls.size());

        return userRepository.findByTelegramId(telegramId)
                .switchIfEmpty(Mono.defer(() -> {
                    return sendMessage(chatId, "❌ Пользователь не найден. Используйте /start для регистрации.")
                            .then(Mono.empty());
                }))
                .flatMap(user -> {
                    // Проверяем баланс
                    return userPointsService.getUserPoints(user.getId())
                            .flatMap(points -> {
                                int requiredPoints = generationProperties.getPointsPerImage();
                                if (points < requiredPoints) {
                                    return sendMessage(chatId,
                                            "❌ *Недостаточно поинтов*\n\n" +
                                                    "💰 *Текущий баланс:* " + points + " поинтов\n" +
                                                    "🎨 *Требуется:* " + requiredPoints + " поинтов для генерации\n\n" +
                                                    "💳 *Пополнить баланс:* https://24reshai.ru/pricing");
                                }

                                // Получаем специальную сессию
                                return telegramBotSessionService.getOrCreateTelegramBotSession(user.getId(), chatId)
                                        .flatMap(session -> {
                                            log.debug("Session получена: sessionId={}", session.getId());
                                            return telegramBotSessionService.getTelegramBotSessionEntityByUserId(user.getId())
                                                    .flatMap(tgSession -> {
                                                        Integer waitingStyle = tgSession.getWaitingStyle();
                                                        log.debug("waitingStyle для userId={}: {}", user.getId(), waitingStyle);
                                                        
                                                        if (waitingStyle != null && waitingStyle == -1) {
                                                            // Ожидание редактирования промпта
                                                            log.debug("Редактирование промпта для userId={}", user.getId());
                                                            return telegramBotSessionService.updatePromptAndInputUrls(user.getId(), prompt, inputImageUrls)
                                                                    .then(telegramBotSessionService.updateWaitingStyle(user.getId(), 0))
                                                                    .then(sendMessage(chatId, String.format("""
                                                                            ✅ *Промпт обновлен!*
                                                                            
                                                                            📝 *Новый промпт:* %s
                                                                            """, prompt)))
                                                                    .then(showStyleSelection(chatId, user.getId(), session.getId(), prompt, inputImageUrls));
                                                        }
                                                        
                                                        if (waitingStyle != null && waitingStyle > 0) {
                                                            // Проверяем, является ли ввод цифрой (выбор стиля)
                                                            try {
                                                                Integer.parseInt(prompt.trim());
                                                                // Это цифра - выбор стиля
                                                                log.debug("Переход в handleStyleSelection");
                                                                return handleStyleSelection(chatId, user.getId(), session.getId(), prompt);
                                                            } catch (NumberFormatException e) {
                                                                // Не цифра - новый промпт, сбрасываем waitingStyle
                                                                log.debug("Ввод не является цифрой, сбрасываем waitingStyle и показываем выбор стиля");
                                                                return telegramBotSessionService.updateWaitingStyle(user.getId(), 0)
                                                                        .then(showStyleSelection(chatId, user.getId(), session.getId(), prompt, inputImageUrls));
                                                            }
                                                        }
                                                        
                                                        // Обычная обработка - показываем выбор стиля
                                                        log.debug("Переход в showStyleSelection");
                                            return showStyleSelection(chatId, user.getId(), session.getId(), prompt, inputImageUrls);
                                                    });
                                        });
                            });
                })
                .doOnSuccess(v -> log.info("Текстовое сообщение обработано для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки текстового сообщения для чата {}", chatId, error));
    }

    /**
     * Обработать фото с описанием.
     *
     * @param chatId     ID чата
     * @param telegramId ID пользователя в Telegram
     * @param photoUrl   URL фото
     * @param caption    описание фото
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
                                int requiredPoints = generationProperties.getPointsPerImage();
                                if (points < requiredPoints) {
                                    return sendMessage(chatId, String.format(
                                            """
                                                    ❌ *Недостаточно поинтов*
                                                    
                                                    💰 *Текущий баланс:* %s поинтов
                                                    🎨 *Требуется:* %s поинтов для генерации
                                                    
                                                    💳 *Пополнить баланс:* https://24reshai.ru/pricing
                                                    """, points, requiredPoints));
                                }

                                // Получаем специальную сессию
                                return telegramBotSessionService.getOrCreateTelegramBotSession(user.getId(), chatId)
                                        .flatMap(session -> {
                                            // Показываем выбор стиля
                                            return showStyleSelection(chatId, user.getId(), session.getId(), caption, List.of(photoUrl));
                                        });
                            });
                })
                .doOnSuccess(v -> log.info("Фото обработано для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки фото для чата {}", chatId, error));
    }

    /**
     * Создать пользователя из данных Telegram.
     */
    private Mono<User> createUserFromTelegram(Long telegramId, String username, String firstName, String lastName) {
        return Mono.fromCallable(() -> {
            String generatedUsername = username != null ? username : "tg_" + telegramId;
            String fullName = firstName != null
                    ? (lastName != null ? firstName + " " + lastName : firstName)
                    : username != null ? username : "tg_" + telegramId;

            return User.builder()
                    .username(generatedUsername)
                    .email(null) // Для пользователей из Telegram email не требуется
                    .password("telegram_auth_" + telegramId) // Временный пароль
                    .emailVerified(false) // У пользователей без email верификация не требуется
                    .telegramId(telegramId)
                    .telegramUsername(username)
                    .telegramFirstName(fullName)
                    .build();
        });
    }

    /**
     * Генерировать изображение с указанным стилем.
     */
    private Mono<Void> generateImage(Long userId, Long sessionId, String prompt, String displayPrompt, List<String> inputImageUrls, Long styleId) {
        log.info("Генерация изображения для пользователя {} в сессии {} с промптом: {} и styleId: {}", 
                userId, sessionId, prompt, styleId);

        // Устанавливаем дефолтное значение styleId = 1, если не указано
        Long finalStyleId = (styleId != null) ? styleId : 1L;
        
        // Создаем запрос для FAL AI (промпт стиля будет добавлен к промпту в FalAIService перед отправкой в FalAI)
        ImageRq imageRq = ImageRq.builder()
                .prompt(prompt) // Оригинальный промпт пользователя
                .sessionId(sessionId)
                .numImages(1)
                .inputImageUrls(inputImageUrls)
                .styleId(finalStyleId)
                .build();

        return falAIService.getImageResponse(imageRq, userId)
                .flatMap(imageResponse -> {
                    // Получаем chatId из специальной сессии
                    return telegramBotSessionService.getTelegramBotSessionEntityByUserId(userId)
                            .flatMap(telegramBotSession -> {
                                Long chatId = telegramBotSession.getChatId();

                                // Отправляем сгенерированные изображения
                                if (imageResponse.getImageUrls().isEmpty()) {
                                    return telegramMessageService.sendErrorMessage(chatId, "Не удалось сгенерировать изображение. Попробуйте еще раз.");
                                }

                                // Отправляем первое изображение с подписью
                                String caption = String.format(
                                        """
                                                🎨 *Изображение сгенерировано!*
                                                
                                                📝 *Промпт:* %s
                                                💰 *Стоимость:* %s поинта
                                                
                                                🔄 *Хотите еще?* Отправьте новое описание для генерации!
                                                
                                                ✏️ *Редактировать изображение?* Ответьте на это сообщение с новым промптом
                                                """,
                                        displayPrompt, generationProperties.getPointsPerImage()
                                );

                                return telegramMessageService.sendPhotoWithMessageId(chatId, imageResponse.getImageUrls().get(0), caption)
                                        .flatMap(messageId -> {
                                            log.info("Сохранение messageId {} для пользователя {}", messageId, userId);
                                            return telegramBotSessionService.updateLastGeneratedMessageId(userId, messageId)
                                                    .then(Mono.just(messageId));
                                        })
                                        .then(Mono.fromRunnable(() -> log.info("Генерация завершена для пользователя {}", userId)))
                                        .then();
                            });
                })
                .onErrorResume(error -> {
                    log.error("Ошибка генерации изображения для пользователя {}: {}", userId, error.getMessage());
                    return telegramBotSessionService.getTelegramBotSessionEntityByUserId(userId)
                            .flatMap(telegramBotSession -> {
                                Long chatId = telegramBotSession.getChatId();
                                // Отправляем сообщение об ошибке
                                return sendMessage(chatId, """
                                        ❌ *Ошибка генерации*
                                        Произошла ошибка при создании изображения. Попробуйте еще раз.""")
                                        .then();
                            });
                });
    }


    /**
     * Отправить приветственное сообщение.
     */
    private Mono<Void> sendWelcomeMessage(Long chatId, String username) {
        String message = String.format(
                """
                        🎉 *Добро пожаловать в 24reshai, %s!*
                        
                        🎨 Вы получили %s поинта при регистрации!
                        🚀 Теперь можете генерировать изображения прямо здесь!
                        
                        📝 *Как начать:*
                        • Отправьте описание изображения
                        • Или приложите фото + описание
                        
                        💰 *Стоимость:* %s поинта за 1 изображение
                        💡 Используйте /help для справки
                        """,
                username, generationProperties.getPointsOnRegistration(), generationProperties.getPointsPerImage()
        );

        return sendMessage(chatId, message);
    }

    /**
     * Обработать обновление от Telegram.
     *`
     * @param update объект обновления от Telegram
     * @return результат обработки
     */
    public Mono<Void> processUpdate(TelegramUpdate update) {
        // Обработка callback query (нажатие на inline-кнопки)
        if (update.getCallbackQuery() != null) {
            return handleCallbackQuery(update.getCallbackQuery());
        }

        if (update.getMessage() == null) {
            log.debug("Обновление не содержит сообщения, пропускаем");
            return Mono.empty();
        }

        TelegramMessage message = update.getMessage();
        Long chatId = message.getChat().getId();
        Long telegramId = message.getFrom().getId();
        String username = message.getFrom().getUsername();
        String firstName = message.getFrom().getFirstName();
        String lastName = message.getFrom().getLastName();

        log.debug("Обработка сообщения от пользователя {} в чате {}: {}", telegramId, chatId,
                message.getText() != null ? message.getText() : "медиа");

        try {
            // Обработка ответов на сообщения (диалог с изображениями)
            if (message.getReplyToMessage() != null) {
                return handleReplyMessage(chatId, telegramId, message);
            }

            // Обработка команд
            if (message.getText() != null && message.getText().startsWith("/")) {
                return handleCommand(chatId, telegramId, username, firstName, lastName, message.getText());
            }

            // Обработка фото с подписью
            if (message.getPhoto() != null && !message.getPhoto().isEmpty() && message.getCaption() != null) {
                TelegramPhoto photo = message.getPhoto().get(message.getPhoto().size() - 1); // Берем фото наибольшего размера
                // Используем прокси URL вместо прямого URL от Telegram
                String proxyUrl = "https://24reshai.ru/api/telegram/proxy/" + photo.getFileId();
                return handlePhotoMessage(chatId, telegramId, proxyUrl, message.getCaption())
                        .onErrorResume(error -> {
                            log.error("Ошибка обработки фото для пользователя {}: {}", telegramId, error.getMessage());
                            return sendMessage(chatId, "❌ *Ошибка загрузки фото*\n\nНе удалось обработать изображение. Попробуйте еще раз.");
                        });
            }

            // Обработка текстового сообщения (промпт)
            if (message.getText() != null && !message.getText().trim().isEmpty()) {
                return handleTextMessage(chatId, telegramId, message.getText());
            }

            // Неизвестный тип сообщения
            log.debug("Получено сообщение неизвестного типа от пользователя {} в чате {}", telegramId, chatId);
            return sendMessage(chatId, """
                    🤔 *Не понимаю*
                    
                    Отправьте текстовое описание для генерации изображения или фото с подписью.
                    """);

        } catch (Exception error) {
            log.error("Ошибка обработки сообщения от пользователя {} в чате {}: {}", telegramId, chatId, error.getMessage(), error);
            return sendMessage(chatId, """
                    ❌ *Произошла ошибка*
                    
                    Попробуйте еще раз или обратитесь в поддержку: https://24reshai.ru/contacts
                    """);
        }
    }

    /**
     * Обработать ответ на сообщение (диалог с изображениями).
     *
     * @param chatId     ID чата
     * @param telegramId ID пользователя в Telegram
     * @param message    сообщение-ответ
     * @return результат обработки
     */
    private Mono<Void> handleReplyMessage(Long chatId, Long telegramId, TelegramMessage message) {
        log.info("Обработка ответа на сообщение от пользователя {} в чате {}", telegramId, chatId);

        TelegramMessage replyToMessage = message.getReplyToMessage();
        Long replyToMessageId = replyToMessage.getMessageId();

        // Сначала находим пользователя по Telegram ID, затем ищем lastGeneratedMessageId
        return userRepository.findByTelegramId(telegramId)
                .switchIfEmpty(Mono.error(new RuntimeException("Пользователь с Telegram ID " + telegramId + " не найден")))
                .flatMap(user -> {
                    log.info("Найден пользователь в базе: ID={}, Telegram ID={}", user.getId(), user.getTelegramId());
                    return telegramBotSessionService.getLastGeneratedMessageId(user.getId())
                            .flatMap(lastGeneratedMessageId -> {
                                log.info("Получен lastGeneratedMessageId для пользователя {}: {}, replyToMessageId: {}",
                                        user.getId(), lastGeneratedMessageId, replyToMessageId);

                                if (!replyToMessageId.equals(lastGeneratedMessageId)) {
                                    log.warn("Пользователь {} ответил на старое сообщение: {} != {}",
                                            user.getId(), replyToMessageId, lastGeneratedMessageId);
                                    return sendMessage(chatId, "❌ *Нельзя ответить на старое сообщение*\n\n" +
                                            "Отвечайте только на последнее сгенерированное изображение.");
                                }

                                // Получаем последнее сгенерированное изображение из Telegram сессии
                                return telegramBotSessionService.getTelegramBotSessionByUserId(user.getId())
                                        .flatMap(session -> imageGenerationHistoryService.getLastGeneratedImageUrlFromSession(user.getId(), session.getId()))
                                        .flatMap(previousImageUrl -> {
                                            String newPrompt = message.getText();
                                            if (newPrompt == null || newPrompt.trim().isEmpty()) {
                                                return sendMessage(chatId, "❌ *Пустой запрос*\n\n" +
                                                        "Отправьте текстовое описание для изменения изображения.");
                                            }

                                            // Для FAL AI используем оригинальный промпт, для отображения - красивый формат
                                            String displayPrompt = String.format("<исходное изображение> %s", newPrompt);

                                            log.info("Диалог с изображением: пользователь {} изменил промпт на '{}'", user.getId(), displayPrompt);

                                            // Получаем сохраненный стиль пользователя или показываем выбор
                                            return telegramBotSessionService.getOrCreateTelegramBotSession(user.getId(), chatId)
                                                    .flatMap(session -> showStyleSelection(chatId, user.getId(), session.getId(), newPrompt, List.of(previousImageUrl)));
                                        });
                            });
                });
    }

    /**
     * Обработать команды.
     */
    private Mono<Void> handleCommand(Long chatId, Long userId, String username, String firstName, String lastName, String command) {
        return switch (command) {
            case "/start" -> handleStartCommand(chatId, userId, username, firstName, lastName);
            case "/help" -> handleHelpCommand(chatId);
            case "/balance" -> handleBalanceCommand(chatId, userId);
            default -> handleUnknownCommand(chatId, command);
        };
    }

    /**
     * Обработать неизвестную команду.
     */
    private Mono<Void> handleUnknownCommand(Long chatId, String command) {
        log.info("Получена неизвестная команда: {} в чате {}", command, chatId);

        return sendMessage(chatId, String.format(
                """
                        ❓ *Неизвестная команда*
                        
                        🤖 *Команда:* %s
                        📋 *Доступные команды:*
                        • /start - Начать работу с ботом
                        • /balance - Баланс поинтов
                        • /help - Справка
                        
                        💡 *Или просто отправьте описание изображения для генерации!*
                        """, command)
        );
    }

    /**
     * Отправить текстовое сообщение.
     */
    public Mono<Void> sendMessage(Long chatId, String message) {
        return telegramMessageService.sendMessage(chatId, message);
    }

    /**
     * Получить промпт и URLs из БД.
     */
    private Mono<TelegramBotSession> getPromptAndInputUrlsFromDB(Long userId) {
        return telegramBotSessionService.getTelegramBotSessionEntityByUserId(userId);
    }

    /**
     * Показать выбор стиля генерации с inline-кнопками.
     */
    private Mono<Void> showStyleSelection(Long chatId, Long userId, Long sessionId, String prompt, List<String> inputImageUrls) {
        log.debug("showStyleSelection вызван для userId={}, prompt={}", userId, prompt);
        
        return telegramBotSessionService.updatePromptAndInputUrls(userId, prompt, inputImageUrls)
                .then(artStyleService.getUserStyle(userId))
                .materialize()
                .flatMap(signal -> {
                    if (signal.hasValue()) {
                        UserStyle userStyle = signal.get();
                        Long styleId = userStyle.getStyleId() != null ? userStyle.getStyleId() : artStyleService.getDefaultUserStyleId();
                        return artStyleService.getStyleById(styleId)
                                .flatMap(style -> {
                                    log.debug("Найден сохраненный стиль для userId={}: {}", userId, style.getName());
                                    String styleDisplay = style.getName();
                                    String message = String.format("""
                                            💡 *Текущий стиль:* %s
                                            
                                            🎨 *Выберите действие:*
                                            """, styleDisplay);
                                    
                                    // Создаем JSON для inline клавиатуры
                                    String keyboardJson = """
                                            {
                                                "inline_keyboard": [
                                                    [{"text": "💡 Улучшить промпт с помощью ИИ", "callback_data": "enhance_prompt:%d:%d"}],
                                                    [{"text": "✏️ Редактировать промпт", "callback_data": "edit_prompt:%d:%d"}],
                                                    [{"text": "🎨 Генерировать с текущим стилем", "callback_data": "generate_current:%d:%d:1"}],
                                                    [{"text": "🔄 Сменить стиль", "callback_data": "change_style:%d:%d:1"}]
                                                ]
                                            }
                                            """.formatted(sessionId, userId, sessionId, userId, sessionId, userId, sessionId, userId);
                                    
                                    return telegramMessageService.sendMessageWithKeyboard(chatId, message, keyboardJson);
                                });
                    } else if (signal.isOnComplete()) {
                        log.debug("Сохраненный стиль не найден для userId={}, показываем список стилей", userId);
                        return showStyleList(chatId, userId, sessionId, prompt, inputImageUrls);
                    } else {
                        log.warn("Ошибка при получении стиля для userId={}", userId);
                        return showStyleList(chatId, userId, sessionId, prompt, inputImageUrls);
                    }
                });
    }

    /**
     * Показать пронумерованный список стилей для выбора.
     */
    private Mono<Void> showStyleList(Long chatId, Long userId, Long sessionId, String prompt, List<String> inputImageUrls) {
        log.debug("showStyleList вызван для sessionId={}, userId={}, prompt={}", sessionId, userId, prompt);
        
        return artStyleService.getStyleById(1L)
                .flatMap(defaultStyle -> artStyleService.getAllStyles()
                        .collectList()
                        .map(styles -> {
                            // Добавляем "Без стиля" в начало (id = 1), если его еще нет в списке
                            List<ArtStyle> allStyles = new ArrayList<>();
                            allStyles.add(defaultStyle);
                            // Фильтруем стили, исключая "Без стиля" если он уже есть в БД
                            styles.stream()
                                    .filter(style -> !style.getId().equals(1L))
                                    .forEach(allStyles::add);
                            return allStyles;
                        }))
                .flatMap(allStyles -> {
                    
                    log.debug("Получено стилей: {}, сохраняем в sessionId={}", allStyles.size(), sessionId);
                    
                    // Сохраняем список стилей в сессию
                    sessionStyles.put(sessionId, allStyles);
                    // Помечаем что сессия ожидает ввода номера
                    telegramBotSessionService.updateWaitingStyle(userId, allStyles.size()).subscribe();
                    log.debug("Установили waitingStyle={} для userId={}", allStyles.size(), userId);
                    
                    // Формируем сообщение со списком стилей
                    StringBuilder styleList = new StringBuilder();
                    styleList.append("🎨 *Выберите стиль для генерации:*\n\n");
                    styleList.append("📝 *Промпт:* ").append(prompt).append("\n\n");
                    if (!inputImageUrls.isEmpty()) {
                        styleList.append("🖼️ *Референс:* загружен\n\n");
                    }
                    styleList.append("💡 *Введите номер стиля:*\n\n");
                    
                    int index = 1;
                    for (ArtStyle style : allStyles) {
                        String emoji = style.getName().equals("none") ? "⚪" : "🎨";
                        styleList.append(index).append(". ").append(emoji).append(" ").append(style.getName()).append("\n");
                        index++;
                    }
                    styleList.append("\nПример: отправьте *1* для выбора без стиля");
                    
                    return sendMessage(chatId, styleList.toString());
                });
    }

    /**
     * Обработать выбор стиля по номеру.
     */
    private Mono<Void> handleStyleSelection(Long chatId, Long userId, Long sessionId, String inputText) {
        log.debug("handleStyleSelection: chatId={}, userId={}, sessionId={}, inputText={}", chatId, userId, sessionId, inputText);
        
        List<ArtStyle> styles = sessionStyles.get(sessionId);
        log.debug("sessionStyles содержит ключи: {}", sessionStyles.keySet());
        
        if (styles == null || styles.isEmpty()) {
            log.warn("Список стилей не найден для sessionId={}, сбрасываем waitingStyle", sessionId);
            // Сбрасываем флаг ожидания и показываем выбор стиля заново
            return telegramBotSessionService.updateWaitingStyle(userId, 0)
                    .then(telegramBotSessionService.getTelegramBotSessionEntityByUserId(userId))
                    .flatMap(tgSession -> {
                        String prompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                        List<String> inputUrls = tgSession.getInputImageUrls() != null 
                                ? telegramBotSessionService.parseInputUrls(tgSession.getInputImageUrls()) 
                                : List.of();
                        return showStyleSelection(chatId, userId, sessionId, prompt, inputUrls);
                    });
        }
        
        try {
            int styleIndex = Integer.parseInt(inputText.trim());
            if (styleIndex < 1 || styleIndex > styles.size()) {
                return sendMessage(chatId, "❌ Неверный номер стиля. Выберите от 1 до " + styles.size());
            }
            
            ArtStyle selectedStyle = styles.get(styleIndex - 1);
            Long styleId = selectedStyle.getId();
            String styleName = selectedStyle.getName();
            
            // Сохраняем стиль пользователя в БД по styleId
            return artStyleService.saveOrUpdateUserStyleById(userId, styleId)
                    .flatMap(saved -> getPromptAndInputUrlsFromDB(userId))
                    .flatMap(tgSession -> {
                        // Получаем промпт и URL фото из БД
                        String prompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                        List<String> inputUrls = tgSession.getInputImageUrls() != null 
                                ? telegramBotSessionService.parseInputUrls(tgSession.getInputImageUrls()) 
                                : List.of();
                        
                        // Очищаем состояние ожидания и временные данные
                        sessionStyles.remove(sessionId);
                        
                        // Запускаем генерацию
                        String styleDisplay = styleName.equals("none") ? "без стиля" : styleName;
                        String message = String.format("""
                                🎨 *Генерация изображения*
                                
                                📝 *Промпт:* %s
                                
                                🎨 *Стиль:* %s
                                
                                ⏱️ *Ожидайте 5-10 секунд*
                                """, prompt, styleDisplay);
                        return telegramBotSessionService.updateWaitingStyle(userId, 0)
                                .then(sendMessage(chatId, message))
                                .then(generateImage(userId, sessionId, prompt, prompt, inputUrls, styleId));
                    });
        } catch (NumberFormatException e) {
            return sendMessage(chatId, "❌ Введите номер стиля (цифру)!");
        }
    }

    /**
     * Обработать callback query от inline-кнопок.
     */
    private Mono<Void> handleCallbackQuery(ru.oparin.troyka.model.dto.telegram.TelegramCallbackQuery callbackQuery) {
        log.info("Обработка callback query: {}", callbackQuery.getId());
        
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChat().getId();
        
        // Парсим callback_data: generate_current:sessionId:userId:hasPhoto
        if (data != null && data.startsWith("generate_current:")) {
            String[] parts = data.split(":", 4);
            if (parts.length >= 4) {
                Long sessionId = Long.parseLong(parts[1]);
                Long userId = Long.parseLong(parts[2]);
                
                // Получаем данные из БД
                return Mono.zip(
                    artStyleService.getUserStyle(userId),
                    getPromptAndInputUrlsFromDB(userId)
                ).flatMap(tuple -> {
                    var userStyle = tuple.getT1();
                    var tgSession = tuple.getT2();
                    
                    // Получаем промпт и URL фото из БД
                    String prompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                    List<String> inputUrls = tgSession.getInputImageUrls() != null 
                            ? telegramBotSessionService.parseInputUrls(tgSession.getInputImageUrls()) 
                            : List.of();
                    
                    // Очищаем URLs и сбрасываем waitingStyle
                    return telegramBotSessionService.clearInputUrls(userId)
                            .then(telegramBotSessionService.updateWaitingStyle(userId, 0))
                            .then(Mono.defer(() -> {
                                // Получаем стиль по styleId
                                Long styleId = userStyle.getStyleId() != null ? userStyle.getStyleId() : artStyleService.getDefaultUserStyleId();
                                return artStyleService.getStyleById(styleId)
                                        .flatMap(style -> {
                                            String styleDisplay = style.getName();
                                            
                                            // Отправляем сообщение о начале генерации
                                            String message = String.format("""
                                                    🎨 *Генерация изображения*
                                                    
                                                    📝 *Промпт:* %s
                                                    
                                                    🎨 *Стиль:* %s
                                                    
                                                    ⏱️ *Ожидайте 5-10 секунд*
                                                    """, prompt, styleDisplay);
                                            return sendMessage(chatId, message)
                                                    .then(generateImage(userId, sessionId, prompt, prompt, inputUrls, styleId));
                                        });
                            }));
                        })
                        .onErrorResume(error -> {
                            log.error("Ошибка при обработке generate_current для userId={}", userId, error);
                            return sendMessage(chatId, "❌ Произошла ошибка при генерации изображения");
                        });
            }
        }
        
        // Парсим callback_data: enhance_prompt:sessionId:userId
        if (data != null && data.startsWith("enhance_prompt:")) {
            String[] parts = data.split(":", 3);
            if (parts.length >= 3) {
                Long sessionId = Long.parseLong(parts[1]);
                Long userId = Long.parseLong(parts[2]);
                
                // Получаем промпт и URL фото из БД
                return getPromptAndInputUrlsFromDB(userId)
                        .flatMap(tgSession -> {
                            String originalPrompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                            List<String> inputUrls = tgSession.getInputImageUrls() != null 
                                    ? telegramBotSessionService.parseInputUrls(tgSession.getInputImageUrls()) 
                                    : List.of();
                            
                            if (originalPrompt.trim().isEmpty()) {
                                return sendMessage(chatId, "❌ Промпт пуст. Отправьте промпт для улучшения.");
                            }
                            
                            // Отправляем сообщение о начале улучшения
                            return sendMessage(chatId, "💡 *Улучшение промпта с помощью ИИ...*\n\n⏱️ *Ожидайте 10-15 секунд*")
                                    .then(artStyleService.getUserStyle(userId))
                                    .switchIfEmpty(Mono.defer(() -> {
                                        // Если стиль не найден, используем дефолтный
                                        return artStyleService.getStyleById(artStyleService.getDefaultUserStyleId())
                                                .map(style -> {
                                                    UserStyle defaultUserStyle = new UserStyle();
                                                    defaultUserStyle.setUserId(userId);
                                                    defaultUserStyle.setStyleId(artStyleService.getDefaultUserStyleId());
                                                    return defaultUserStyle;
                                                });
                                    }))
                                    .flatMap(userStyle -> {
                                        Long styleId = userStyle.getStyleId() != null ? userStyle.getStyleId() : artStyleService.getDefaultUserStyleId();
                                        return artStyleService.getStyleById(styleId)
                                                .flatMap(style -> promptEnhancementService.enhancePrompt(originalPrompt, inputUrls, style))
                                                .flatMap(enhancedPrompt -> {
                                                    // Обновляем промпт в БД
                                                    return telegramBotSessionService.updatePromptAndInputUrls(userId, enhancedPrompt, inputUrls)
                                                            // Отправляем чистый промпт для копирования
                                                            .then(sendMessage(chatId, enhancedPrompt))
                                                            .then(showStyleSelection(chatId, userId, sessionId, enhancedPrompt, inputUrls));
                                                })
                                                .onErrorResume(error -> {
                                                    log.error("Ошибка улучшения промпта для userId={}", userId, error);
                                                    return sendMessage(chatId, "❌ Не удалось улучшить промпт. Попробуйте еще раз или используйте оригинальный промпт.");
                                                });
                                    });
                        });
            }
        }
        
        // Парсим callback_data: edit_prompt:sessionId:userId
        if (data != null && data.startsWith("edit_prompt:")) {
            String[] parts = data.split(":", 3);
            if (parts.length >= 3) {
                Long sessionId = Long.parseLong(parts[1]);
                Long userId = Long.parseLong(parts[2]);
                
                // Устанавливаем флаг ожидания редактирования промпта
                return telegramBotSessionService.updateWaitingStyle(userId, -1)
                        .then(sendMessage(chatId, """
                                ✏️ *Редактирование промпта*
                                
                                📝 Отправьте новый текст промпта для замены текущего.
                                
                                💡 Вы можете скопировать и скорректировать улучшенный промпт или написать свой.
                                """));
            }
        }
        
        // Парсим callback_data: change_style:sessionId:userId:hasPhoto
        if (data != null && data.startsWith("change_style:")) {
            String[] parts = data.split(":", 4);
            if (parts.length >= 4) {
                Long sessionId = Long.parseLong(parts[1]);
                Long userId = Long.parseLong(parts[2]);
                
                // Получаем промпт и URL фото из БД
                return getPromptAndInputUrlsFromDB(userId)
                        .flatMap(tgSession -> {
                            String prompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                            List<String> inputUrls = tgSession.getInputImageUrls() != null 
                                    ? telegramBotSessionService.parseInputUrls(tgSession.getInputImageUrls()) 
                                    : List.of();
                            return showStyleList(chatId, userId, sessionId, prompt, inputUrls);
                        });
            }
        }
        
        // Парсим callback_data: style:styleName:sessionId:userId:hasPhoto
        if (data != null && data.startsWith("style:")) {
            String[] parts = data.split(":", 5);
            if (parts.length >= 5) {
                String styleName = parts[1];
                Long sessionId = Long.parseLong(parts[2]);
                Long userId = Long.parseLong(parts[3]);
                
                // Получаем промпт и URL фото из БД
                return getPromptAndInputUrlsFromDB(userId)
                        .flatMap(tgSession -> {
                            String prompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                            List<String> inputUrls = tgSession.getInputImageUrls() != null 
                                    ? telegramBotSessionService.parseInputUrls(tgSession.getInputImageUrls()) 
                                    : List.of();
                            
                            // Очищаем URLs после использования
                            telegramBotSessionService.clearInputUrls(userId).subscribe();
                
                // Получаем стиль по имени и его id
                            return artStyleService.getStyleByName(styleName)
                                    .switchIfEmpty(artStyleService.getStyleById(artStyleService.getDefaultUserStyleId()))
                                    .flatMap(style -> {
                                        Long styleId = style.getId();
                                        String styleDisplay = style.getName();
                                        
                                        // Сохраняем стиль в БД по id
                                        artStyleService.saveOrUpdateUserStyleById(userId, styleId).subscribe();
                                        
                                        // Отправляем сообщение о начале генерации
                                        String message = String.format("🎨 *Генерация изображения*\n\n📝 *Промпт:* %s\n\n🎨 *Стиль:* %s\n\n⏱️ *Ожидайте 5-10 секунд*", prompt, styleDisplay);
                                        return sendMessage(chatId, message)
                                                .then(generateImage(userId, sessionId, prompt, prompt, inputUrls, styleId));
                                    });
                        });
            }
        }
        
        // Отвечаем на callback
        return telegramMessageService.answerCallbackQuery(callbackQuery.getId())
                .then();
    }

}
