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
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.repository.UserRepository;
import ru.oparin.troyka.service.ArtStyleService;
import ru.oparin.troyka.service.FalAIService;
import ru.oparin.troyka.service.ImageGenerationHistoryService;
import ru.oparin.troyka.service.UserPointsService;

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
    
    // Временное хранилище для промпта и URL фото по сессии
    private final Map<Long, String> sessionPrompts = new HashMap<>();
    private final Map<Long, List<String>> sessionInputUrls = new HashMap<>();
    private final Map<Long, List<ArtStyle>> sessionStyles = new HashMap<>(); // список стилей для выбора

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
                                                        
                                                        if (waitingStyle != null && waitingStyle > 0) {
                                                            // Пользователь выбирает стиль - обрабатываем номер
                                                            log.debug("Переход в handleStyleSelection");
                                                            return handleStyleSelection(chatId, user.getId(), session.getId(), prompt);
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
    private Mono<Void> generateImage(Long userId, Long sessionId, String prompt, String displayPrompt, List<String> inputImageUrls, String styleName) {
        log.info("Генерация изображения для пользователя {} в сессии {} с промптом: {} и стилем: {}", 
                userId, sessionId, prompt, styleName);

        // Применяем стиль к промпту
        Mono<String> finalPromptMono;
        if (!styleName.equals("none")) {
            finalPromptMono = artStyleService.getStyleByName(styleName)
                    .map(style -> {
                        String stylePrompt = ", " + style.getPrompt();
                        return prompt + stylePrompt;
                    })
                    .defaultIfEmpty(prompt);
        } else {
            finalPromptMono = Mono.just(prompt);
        }

        // Создаем запрос для FAL AI
        return finalPromptMono.flatMap(finalPrompt -> {
            ImageRq imageRq = ImageRq.builder()
                    .prompt(finalPrompt)
                    .sessionId(sessionId)
                    .numImages(1)
                    .inputImageUrls(inputImageUrls)
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
                                                
                                                🔄 *Хотите еще?* Просто отправьте новое описание!
                                                """,
                                        displayPrompt, generationProperties.getPointsPerImage()
                                );

                                return telegramMessageService.sendPhotoWithMessageId(chatId, imageResponse.getImageUrls().get(0), caption)
                                        .flatMap(messageId -> {
                                            log.info("Сохранение messageId {} для пользователя {}", messageId, userId);
                                            return telegramBotSessionService.updateLastGeneratedMessageId(userId, messageId)
                                                    .then(Mono.just(messageId));
                                        })
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

                                            // Генерируем новое изображение с предыдущим как input
                                            return telegramBotSessionService.getOrCreateTelegramBotSession(user.getId(), chatId)
                                                    .flatMap(session -> generateImage(user.getId(), session.getId(), newPrompt, displayPrompt, List.of(previousImageUrl), "none"));
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
     * Показать выбор стиля генерации с inline-кнопками.
     */
    private Mono<Void> showStyleSelection(Long chatId, Long userId, Long sessionId, String prompt, List<String> inputImageUrls) {
        // Сохраняем промпт и URL фото для последующего использования
        sessionPrompts.put(sessionId, prompt);
        sessionInputUrls.put(sessionId, inputImageUrls);
        
        // Всегда показываем список стилей
        return showStyleList(chatId, userId, sessionId, prompt, inputImageUrls);
    }

    /**
     * Показать пронумерованный список стилей для выбора.
     */
    private Mono<Void> showStyleList(Long chatId, Long userId, Long sessionId, String prompt, List<String> inputImageUrls) {
        log.debug("showStyleList: получение стилей для sessionId={}", sessionId);
        
        return artStyleService.getAllStyles()
                .collectList()
                .flatMap(styles -> {
                    // Добавляем "Без стиля" в начало
                    List<ArtStyle> allStyles = new ArrayList<>();
                    allStyles.add(ArtStyle.builder().name("none").prompt("").build());
                    allStyles.addAll(styles);
                    
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
                    .then(Mono.fromRunnable(() -> {
                        String prompt = sessionPrompts.getOrDefault(sessionId, "");
                        List<String> inputUrls = sessionInputUrls.getOrDefault(sessionId, List.of());
                        showStyleSelection(chatId, userId, sessionId, prompt, inputUrls).subscribe();
                    }))
                    .then();
        }
        
        try {
            int styleIndex = Integer.parseInt(inputText.trim());
            if (styleIndex < 1 || styleIndex > styles.size()) {
                return sendMessage(chatId, "❌ Неверный номер стиля. Выберите от 1 до " + styles.size());
            }
            
            ArtStyle selectedStyle = styles.get(styleIndex - 1);
            String styleName = selectedStyle.getName();
            
            // Сохраняем стиль пользователя в БД
            return artStyleService.saveOrUpdateUserStyle(userId, styleName)
                    .flatMap(saved -> {
                        // Получаем промпт и URL фото
                        String prompt = sessionPrompts.getOrDefault(sessionId, "");
                        List<String> inputUrls = sessionInputUrls.getOrDefault(sessionId, List.of());
                        
                        // Очищаем состояние ожидания и временные данные
                        telegramBotSessionService.updateWaitingStyle(userId, 0).subscribe();
                        sessionStyles.remove(sessionId);
                        
                        // Запускаем генерацию
                        String styleDisplay = styleName.equals("none") ? "без стиля" : styleName;
                        String message = String.format("""
                                🎨 *Генерация изображения*
                                
                                📝 *Промпт:* %s
                                
                                🎨 *Стиль:* %s
                                
                                ⏱️ *Ожидайте 5-10 секунд*
                                """, prompt, styleDisplay);
                        return sendMessage(chatId, message)
                                .then(generateImage(userId, sessionId, prompt, prompt, inputUrls, styleName));
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
        
        // Парсим callback_data: style:styleName:sessionId:userId:hasPhoto
        if (data != null && data.startsWith("style:")) {
            String[] parts = data.split(":", 5);
            if (parts.length >= 5) {
                String styleName = parts[1];
                Long sessionId = Long.parseLong(parts[2]);
                Long userId = Long.parseLong(parts[3]);
                
                // Получаем промпт и URL фото из временного хранилища
                String prompt = sessionPrompts.getOrDefault(sessionId, "");
                List<String> inputUrls = sessionInputUrls.getOrDefault(sessionId, List.of());
                
                // Очищаем временные данные после использования
                sessionPrompts.remove(sessionId);
                sessionInputUrls.remove(sessionId);
                
                // Получаем стиль для отображения
                String styleDisplay = styleName.equals("none") ? "без стиля" : styleName;
                
                // Отправляем сообщение о начале генерации
                String message = String.format("🎨 *Генерация изображения*\n\n📝 *Промпт:* %s\n\n🎨 *Стиль:* %s\n\n⏱️ *Ожидайте 5-10 секунд*", prompt, styleDisplay);
                return sendMessage(chatId, message)
                        .then(generateImage(userId, sessionId, prompt, prompt, inputUrls, styleName));
            }
        }
        
        // Отвечаем на callback
        return telegramMessageService.answerCallbackQuery(callbackQuery.getId())
                .then();
    }

}
