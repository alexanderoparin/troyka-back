package ru.oparin.troyka.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.dto.telegram.*;
import ru.oparin.troyka.model.entity.ArtStyle;
import ru.oparin.troyka.model.entity.TelegramBotSession;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.model.entity.UserStyle;
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

    // Константы для состояний waitingStyle
    private static final int WAITING_STYLE_EDIT_PROMPT = -1;
    private static final int WAITING_STYLE_NONE = 0;
    private static final long DEFAULT_STYLE_ID = 1L;
    private static final String DEFAULT_STYLE_NAME = "none";
    private static final String TELEGRAM_PROXY_URL_PREFIX = "https://24reshai.ru/api/telegram/proxy/";
    private static final String PRICING_URL = "https://24reshai.ru/pricing";
    private static final String SUPPORT_URL = "https://24reshai.ru/contacts";
    private static final String SITE_URL = "https://24reshai.ru";

    private final UserService userService;
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

    /**
     * Обработать команду /start.
     */
    public Mono<Void> handleStartCommand(Long chatId, Long telegramId, String username, String firstName, String lastName) {
        log.info("Обработка команды /start для чата {} и пользователя {}", chatId, telegramId);

        return findOrCreateUser(telegramId, username, firstName, lastName, chatId)
                .flatMap(user -> sendWelcomeMessage(chatId, user.getUsername()))
                .then()
                .doOnSuccess(v -> log.info("Команда /start обработана для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки команды /start для чата {}", chatId, error));
    }

    /**
     * Обработать команду /help.
     */
    public Mono<Void> handleHelpCommand(Long chatId) {
        log.info("Обработка команды /help для чата {}", chatId);
        return sendMessage(chatId, buildHelpMessage())
                .doOnSuccess(v -> log.info("Команда /help обработана для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки команды /help для чата {}", chatId, error));
    }

    /**
     * Обработать команду /balance.
     */
    public Mono<Void> handleBalanceCommand(Long chatId, Long telegramId) {
        log.info("Обработка команды /balance для чата {} и пользователя {}", chatId, telegramId);

        return findUserByTelegramId(telegramId, chatId)
                .flatMap(user -> userPointsService.getUserPoints(user.getId())
                        .map(this::buildBalanceMessage)
                        .flatMap(message -> sendMessage(chatId, message)))
                .doOnSuccess(v -> log.info("Команда /balance обработана для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки команды /balance для чата {}", chatId, error));
    }

    /**
     * Обработать текстовое сообщение (промпт для генерации).
     */
    public Mono<Void> handleTextMessage(Long chatId, Long telegramId, String prompt) {
        return handleTextMessage(chatId, telegramId, prompt, List.of());
    }

    /**
     * Обработать текстовое сообщение с входными изображениями.
     */
    public Mono<Void> handleTextMessage(Long chatId, Long telegramId, String prompt, List<String> inputImageUrls) {
        log.info("Обработка текстового сообщения для чата {} и пользователя {}: {} (входных изображений: {})",
                chatId, telegramId, prompt, inputImageUrls.size());

        return findUserByTelegramId(telegramId, chatId)
                .flatMap(user -> checkBalanceAndProcess(user, chatId, prompt, inputImageUrls))
                .doOnSuccess(v -> log.info("Текстовое сообщение обработано для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки текстового сообщения для чата {}", chatId, error));
    }

    /**
     * Обработать фото с описанием.
     */
    public Mono<Void> handlePhotoMessage(Long chatId, Long telegramId, String photoUrl, String caption) {
        log.info("Обработка фото для чата {} и пользователя {}: {}", chatId, telegramId, caption);

        return findUserByTelegramId(telegramId, chatId)
                .flatMap(user -> checkBalanceAndShowStyleSelection(user, chatId, caption, List.of(photoUrl)))
                .doOnSuccess(v -> log.info("Фото обработано для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки фото для чата {}", chatId, error));
    }

    /**
     * Обработать обновление от Telegram.
     */
    public Mono<Void> processUpdate(TelegramUpdate update) {
        if (update.getCallbackQuery() != null) {
            return updateUserDataFromTelegram(update.getCallbackQuery().getFrom())
                    .then(handleCallbackQuery(update.getCallbackQuery()));
        }

        if (update.getMessage() == null) {
            log.debug("Обновление не содержит сообщения, пропускаем");
            return Mono.empty();
        }

        TelegramMessage message = update.getMessage();
        Long chatId = message.getChat().getId();
        TelegramUser telegramUser = message.getFrom();
        Long telegramId = telegramUser.getId();

        log.debug("Обработка сообщения от пользователя {} в чате {}: {}",
                telegramId, chatId, message.getText() != null ? message.getText() : "медиа");

        return updateUserDataFromTelegram(telegramUser)
                .then(processMessage(message, chatId, telegramId))
                .onErrorResume(error -> {
                    log.error("Ошибка обработки сообщения от пользователя {} в чате {}: {}",
                            telegramId, chatId, error.getMessage(), error);
                    return sendMessage(chatId, buildErrorMessage());
                });
    }

    // ==================== Вспомогательные методы ====================

    /**
     * Найти или создать пользователя.
     */
    private Mono<User> findOrCreateUser(Long telegramId, String username, String firstName, String lastName, Long chatId) {
        return userService.findByTelegramId(telegramId)
                .switchIfEmpty(Mono.defer(() -> createNewUser(telegramId, username, firstName, lastName, chatId)));
    }

    /**
     * Создать нового пользователя из Telegram.
     */
    private Mono<User> createNewUser(Long telegramId, String username, String firstName, String lastName, Long chatId) {
        return createUserFromTelegram(telegramId, username, firstName, lastName)
                .flatMap(user -> userService.saveUser(user)
                        .flatMap(savedUser -> userPointsService.addPointsToUser(
                                        savedUser.getId(), generationProperties.getPointsOnRegistration())
                                .then(telegramBotSessionService.getOrCreateTelegramBotSession(savedUser.getId(), chatId))
                                .thenReturn(savedUser)));
    }

    /**
     * Найти пользователя по Telegram ID или отправить сообщение об ошибке.
     */
    private Mono<User> findUserByTelegramId(Long telegramId, Long chatId) {
        return userService.findByTelegramId(telegramId)
                .switchIfEmpty(Mono.defer(() -> sendMessage(chatId, buildUserNotFoundMessage())
                        .then(Mono.empty())));
    }

    /**
     * Проверить баланс и обработать запрос.
     */
    private Mono<Void> checkBalanceAndProcess(User user, Long chatId, String prompt, List<String> inputImageUrls) {
        return userPointsService.getUserPoints(user.getId())
                .flatMap(points -> {
                    if (points < generationProperties.getPointsPerImage()) {
                        return sendMessage(chatId, buildInsufficientPointsMessage(points));
                    }
                    return processTextMessageWithBalance(user, chatId, prompt, inputImageUrls);
                });
    }

    /**
     * Обработать текстовое сообщение после проверки баланса.
     */
    private Mono<Void> processTextMessageWithBalance(User user, Long chatId, String prompt, List<String> inputImageUrls) {
        return telegramBotSessionService.getOrCreateTelegramBotSession(user.getId(), chatId)
                .flatMap(session -> telegramBotSessionService.getTelegramBotSessionEntityByUserId(user.getId())
                        .flatMap(tgSession -> handleTextMessageByWaitingStyle(
                                user, chatId, session.getId(), prompt, inputImageUrls, tgSession.getWaitingStyle())));
    }

    /**
     * Обработать текстовое сообщение в зависимости от состояния waitingStyle.
     */
    private Mono<Void> handleTextMessageByWaitingStyle(User user, Long chatId, Long sessionId,
                                                        String prompt, List<String> inputImageUrls, Integer waitingStyle) {
        if (waitingStyle != null && waitingStyle == WAITING_STYLE_EDIT_PROMPT) {
            return handlePromptEdit(user.getId(), chatId, sessionId, prompt, inputImageUrls);
        }

        if (waitingStyle != null && waitingStyle > WAITING_STYLE_NONE) {
            return handleStyleNumberInput(chatId, user.getId(), sessionId, prompt, waitingStyle);
        }

        return showStyleSelection(chatId, user.getId(), sessionId, prompt, inputImageUrls);
    }

    /**
     * Обработать редактирование промпта.
     */
    private Mono<Void> handlePromptEdit(Long userId, Long chatId, Long sessionId, String prompt, List<String> inputImageUrls) {
        log.debug("Редактирование промпта для userId={}", userId);
        return telegramBotSessionService.updatePromptAndInputUrls(userId, prompt, inputImageUrls)
                .then(telegramBotSessionService.updateWaitingStyle(userId, WAITING_STYLE_NONE))
                .then(sendMessage(chatId, buildPromptUpdatedMessage(prompt)))
                .then(showStyleSelection(chatId, userId, sessionId, prompt, inputImageUrls));
    }

    /**
     * Обработать ввод номера стиля.
     */
    private Mono<Void> handleStyleNumberInput(Long chatId, Long userId, Long sessionId, String prompt, Integer waitingStyle) {
        if (isNumeric(prompt)) {
            log.debug("Переход в handleStyleSelection");
            return handleStyleSelection(chatId, userId, sessionId, prompt);
        } else {
            log.debug("Ввод не является цифрой, сбрасываем waitingStyle и показываем выбор стиля");
            return telegramBotSessionService.updateWaitingStyle(userId, WAITING_STYLE_NONE)
                    .then(getPromptAndInputUrlsFromDB(userId))
                    .flatMap(tgSession -> showStyleSelection(chatId, userId, sessionId, prompt,
                            parseInputUrls(tgSession.getInputImageUrls())));
        }
    }

    /**
     * Проверить баланс и показать выбор стиля.
     */
    private Mono<Void> checkBalanceAndShowStyleSelection(User user, Long chatId, String prompt, List<String> inputImageUrls) {
        return userPointsService.getUserPoints(user.getId())
                .flatMap(points -> {
                    if (points < generationProperties.getPointsPerImage()) {
                        return sendMessage(chatId, buildInsufficientPointsMessage(points));
                    }
                    return telegramBotSessionService.getOrCreateTelegramBotSession(user.getId(), chatId)
                            .flatMap(session -> showStyleSelection(chatId, user.getId(), session.getId(), prompt, inputImageUrls));
                });
    }

    /**
     * Обновить данные пользователя из Telegram.
     */
    private Mono<Boolean> updateUserTelegramData(User user, TelegramUser telegramUser) {
        return Mono.fromCallable(() -> {
            if (user == null || telegramUser == null) {
                return false;
            }

            boolean hasChanges = false;
            Long telegramId = telegramUser.getId();
            String username = telegramUser.getUsername();
            String firstName = telegramUser.getFirstName();
            String lastName = telegramUser.getLastName();

            if (telegramId != null && !telegramId.equals(user.getTelegramId())) {
                user.setTelegramId(telegramId);
                hasChanges = true;
            }

            if (username != null && !username.equals(user.getTelegramUsername())) {
                user.setTelegramUsername(username);
                hasChanges = true;
            }

            String fullName = buildFullName(firstName, lastName);
            if (fullName != null && !fullName.equals(user.getTelegramFirstName())) {
                user.setTelegramFirstName(fullName);
                hasChanges = true;
            }

            return hasChanges;
        });
    }

    /**
     * Обновить данные пользователя из Telegram при взаимодействии.
     */
    private Mono<Void> updateUserDataFromTelegram(TelegramUser telegramUser) {
        return userService.findByTelegramId(telegramUser.getId())
                .flatMap(user -> updateUserTelegramData(user, telegramUser)
                        .flatMap(hasChanges -> hasChanges
                                ? userService.saveUser(user).thenReturn(true)
                                : Mono.just(false)))
                .then();
    }

    /**
     * Создать пользователя из данных Telegram.
     */
    private Mono<User> createUserFromTelegram(Long telegramId, String username, String firstName, String lastName) {
        return Mono.fromCallable(() -> {
            String generatedUsername = username != null ? username : "tg_" + telegramId;
            String fullName = buildFullName(firstName, lastName);
            if (fullName == null) {
                fullName = username != null ? username : "tg_" + telegramId;
            }

            return User.builder()
                    .username(generatedUsername)
                    .email(null)
                    .password("telegram_auth_" + telegramId)
                    .emailVerified(false)
                    .telegramId(telegramId)
                    .telegramUsername(username)
                    .telegramFirstName(fullName)
                    .build();
        });
    }

    /**
     * Построить полное имя из firstName и lastName.
     */
    private String buildFullName(String firstName, String lastName) {
        if (firstName != null) {
            return lastName != null ? firstName + " " + lastName : firstName;
        }
        return lastName;
    }

    /**
     * Генерировать изображение с указанным стилем.
     */
    private Mono<Void> generateImage(Long userId, Long sessionId, String prompt, String displayPrompt,
                                      List<String> inputImageUrls, Long styleId) {
        log.info("Генерация изображения для пользователя {} в сессии {} с промптом: {} и styleId: {}",
                userId, sessionId, prompt, styleId);

        Long finalStyleId = styleId != null ? styleId : DEFAULT_STYLE_ID;
        ImageRq imageRq = buildImageRequest(prompt, sessionId, inputImageUrls, finalStyleId);

        return falAIService.getImageResponse(imageRq, userId)
                .flatMap(imageResponse -> sendGeneratedImage(userId, imageResponse, displayPrompt))
                .onErrorResume(error -> handleGenerationError(userId, error));
    }

    /**
     * Построить запрос на генерацию изображения.
     */
    private ImageRq buildImageRequest(String prompt, Long sessionId, List<String> inputImageUrls, Long styleId) {
        return ImageRq.builder()
                .prompt(prompt)
                .sessionId(sessionId)
                .numImages(1)
                .inputImageUrls(inputImageUrls)
                .styleId(styleId)
                .build();
    }

    /**
     * Отправить сгенерированное изображение пользователю.
     */
    private Mono<Void> sendGeneratedImage(Long userId, ru.oparin.troyka.model.dto.fal.ImageRs imageResponse, String displayPrompt) {
        return telegramBotSessionService.getTelegramBotSessionEntityByUserId(userId)
                .flatMap(telegramBotSession -> {
                    Long chatId = telegramBotSession.getChatId();

                    if (imageResponse.getImageUrls().isEmpty()) {
                        return telegramMessageService.sendErrorMessage(chatId,
                                "Не удалось сгенерировать изображение. Попробуйте еще раз.");
                    }

                    String caption = buildImageGeneratedCaption(displayPrompt);
                    return telegramMessageService.sendPhotoWithMessageId(chatId, imageResponse.getImageUrls().get(0), caption)
                            .flatMap(messageId -> {
                                log.info("Сохранение messageId {} для пользователя {}", messageId, userId);
                                return telegramBotSessionService.updateLastGeneratedMessageId(userId, messageId)
                                        .then(Mono.just(messageId));
                            })
                            .then(Mono.fromRunnable(() -> log.info("Генерация завершена для пользователя {}", userId)))
                            .then();
                });
    }

    /**
     * Обработать ошибку генерации.
     */
    private Mono<Void> handleGenerationError(Long userId, Throwable error) {
        log.error("Ошибка генерации изображения для пользователя {}: {}", userId, error.getMessage());
        return telegramBotSessionService.getTelegramBotSessionEntityByUserId(userId)
                .flatMap(telegramBotSession -> {
                    Long chatId = telegramBotSession.getChatId();
                    return sendMessage(chatId, buildGenerationErrorMessage());
                });
    }

    /**
     * Обработать сообщение.
     */
    private Mono<Void> processMessage(TelegramMessage message, Long chatId, Long telegramId) {
        if (message.getReplyToMessage() != null) {
            return handleReplyMessage(chatId, telegramId, message);
        }

        if (message.getText() != null && message.getText().startsWith("/")) {
            TelegramUser telegramUser = message.getFrom();
            return handleCommand(chatId, telegramId, telegramUser.getUsername(),
                    telegramUser.getFirstName(), telegramUser.getLastName(), message.getText());
        }

        if (message.getPhoto() != null && !message.getPhoto().isEmpty() && message.getCaption() != null) {
            TelegramPhoto photo = message.getPhoto().get(message.getPhoto().size() - 1);
            String proxyUrl = TELEGRAM_PROXY_URL_PREFIX + photo.getFileId();
            return handlePhotoMessage(chatId, telegramId, proxyUrl, message.getCaption())
                    .onErrorResume(error -> {
                        log.error("Ошибка обработки фото для пользователя {}: {}", telegramId, error.getMessage());
                        return sendMessage(chatId, buildPhotoErrorMessage());
                    });
        }

        if (message.getText() != null && !message.getText().trim().isEmpty()) {
            return handleTextMessage(chatId, telegramId, message.getText());
        }

        log.debug("Получено сообщение неизвестного типа от пользователя {} в чате {}", telegramId, chatId);
        return sendMessage(chatId, buildUnknownMessageTypeMessage());
    }

    /**
     * Обработать ответ на сообщение (диалог с изображениями).
     */
    private Mono<Void> handleReplyMessage(Long chatId, Long telegramId, TelegramMessage message) {
        log.info("Обработка ответа на сообщение от пользователя {} в чате {}", telegramId, chatId);

        Long replyToMessageId = message.getReplyToMessage().getMessageId();

        return findUserByTelegramId(telegramId, chatId)
                .flatMap(user -> telegramBotSessionService.getLastGeneratedMessageId(user.getId())
                        .flatMap(lastGeneratedMessageId -> {
                            if (!replyToMessageId.equals(lastGeneratedMessageId)) {
                                log.warn("Пользователь {} ответил на старое сообщение: {} != {}",
                                        user.getId(), replyToMessageId, lastGeneratedMessageId);
                                return sendMessage(chatId, buildOldMessageReplyMessage());
                            }

                            return processImageEdit(user, chatId, message.getText());
                        }));
    }

    /**
     * Обработать редактирование изображения.
     */
    private Mono<Void> processImageEdit(User user, Long chatId, String newPrompt) {
        if (newPrompt == null || newPrompt.trim().isEmpty()) {
            return sendMessage(chatId, buildEmptyPromptMessage());
        }

        String displayPrompt = String.format("<исходное изображение> %s", newPrompt);
        log.info("Диалог с изображением: пользователь {} изменил промпт на '{}'", user.getId(), displayPrompt);

        return telegramBotSessionService.getTelegramBotSessionByUserId(user.getId())
                .flatMap(session -> imageGenerationHistoryService.getLastGeneratedImageUrlFromSession(
                        user.getId(), session.getId()))
                .flatMap(previousImageUrl -> telegramBotSessionService.getOrCreateTelegramBotSession(user.getId(), chatId)
                        .flatMap(session -> showStyleSelection(chatId, user.getId(), session.getId(),
                                newPrompt, List.of(previousImageUrl))));
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
        return sendMessage(chatId, buildUnknownCommandMessage(command));
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
     * Парсить inputUrls из JSON строки.
     */
    private List<String> parseInputUrls(String inputUrlsJson) {
        if (inputUrlsJson == null || inputUrlsJson.isEmpty()) {
            return List.of();
        }
        return telegramBotSessionService.parseInputUrls(inputUrlsJson);
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
                        return showStyleSelectionWithSavedStyle(chatId, userId, sessionId, signal.get());
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
     * Показать выбор стиля с сохраненным стилем пользователя.
     */
    private Mono<Void> showStyleSelectionWithSavedStyle(Long chatId, Long userId, Long sessionId, UserStyle userStyle) {
        Long styleId = userStyle.getStyleId() != null ? userStyle.getStyleId() : artStyleService.getDefaultUserStyleId();
        return artStyleService.getStyleById(styleId)
                .flatMap(style -> {
                    log.debug("Найден сохраненный стиль для userId={}: {}", userId, style.getName());
                    String message = buildStyleSelectionMessage(style.getName());
                    String keyboardJson = buildStyleSelectionKeyboard(sessionId, userId);
                    return telegramMessageService.sendMessageWithKeyboard(chatId, message, keyboardJson);
                });
    }

    /**
     * Показать пронумерованный список стилей для выбора.
     */
    private Mono<Void> showStyleList(Long chatId, Long userId, Long sessionId, String prompt, List<String> inputImageUrls) {
        log.debug("showStyleList вызван для sessionId={}, userId={}, prompt={}", sessionId, userId, prompt);

        return artStyleService.getStyleById(DEFAULT_STYLE_ID)
                .flatMap(defaultStyle -> artStyleService.getAllStyles()
                        .collectList()
                        .map(styles -> buildAllStylesList(defaultStyle, styles)))
                .flatMap(allStyles -> {
                    log.debug("Получено стилей: {}, сохраняем в sessionId={}", allStyles.size(), sessionId);
                    sessionStyles.put(sessionId, allStyles);
                    telegramBotSessionService.updateWaitingStyle(userId, allStyles.size()).subscribe();
                    log.debug("Установили waitingStyle={} для userId={}", allStyles.size(), userId);

                    String message = buildStyleListMessage(prompt, inputImageUrls, allStyles);
                    return sendMessage(chatId, message);
                });
    }

    /**
     * Построить список всех стилей с дефолтным в начале.
     */
    private List<ArtStyle> buildAllStylesList(ArtStyle defaultStyle, List<ArtStyle> styles) {
        List<ArtStyle> allStyles = new ArrayList<>();
        allStyles.add(defaultStyle);
        styles.stream()
                .filter(style -> !style.getId().equals(DEFAULT_STYLE_ID))
                .forEach(allStyles::add);
        return allStyles;
    }

    /**
     * Обработать выбор стиля по номеру.
     */
    private Mono<Void> handleStyleSelection(Long chatId, Long userId, Long sessionId, String inputText) {
        log.debug("handleStyleSelection: chatId={}, userId={}, sessionId={}, inputText={}", chatId, userId, sessionId, inputText);

        List<ArtStyle> styles = sessionStyles.get(sessionId);
        if (styles == null || styles.isEmpty()) {
            log.warn("Список стилей не найден для sessionId={}, сбрасываем waitingStyle", sessionId);
            return resetWaitingStyleAndShowSelection(chatId, userId, sessionId);
        }

        try {
            int styleIndex = Integer.parseInt(inputText.trim());
            if (styleIndex < 1 || styleIndex > styles.size()) {
                return sendMessage(chatId, "❌ Неверный номер стиля. Выберите от 1 до " + styles.size());
            }

            ArtStyle selectedStyle = styles.get(styleIndex - 1);
            return processStyleSelection(chatId, userId, sessionId, selectedStyle);
        } catch (NumberFormatException e) {
            return sendMessage(chatId, "❌ Введите номер стиля (цифру)!");
        }
    }

    /**
     * Сбросить waitingStyle и показать выбор стиля заново.
     */
    private Mono<Void> resetWaitingStyleAndShowSelection(Long chatId, Long userId, Long sessionId) {
        return telegramBotSessionService.updateWaitingStyle(userId, WAITING_STYLE_NONE)
                .then(getPromptAndInputUrlsFromDB(userId))
                .flatMap(tgSession -> {
                    String prompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                    List<String> inputUrls = parseInputUrls(tgSession.getInputImageUrls());
                    return showStyleSelection(chatId, userId, sessionId, prompt, inputUrls);
                });
    }

    /**
     * Обработать выбранный стиль.
     */
    private Mono<Void> processStyleSelection(Long chatId, Long userId, Long sessionId, ArtStyle selectedStyle) {
        Long styleId = selectedStyle.getId();
        String styleName = selectedStyle.getName();

        return artStyleService.saveOrUpdateUserStyleById(userId, styleId)
                .flatMap(saved -> getPromptAndInputUrlsFromDB(userId))
                .flatMap(tgSession -> {
                    String prompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                    List<String> inputUrls = parseInputUrls(tgSession.getInputImageUrls());

                    sessionStyles.remove(sessionId);
                    String styleDisplay = styleName.equals(DEFAULT_STYLE_NAME) ? "без стиля" : styleName;
                    String message = buildGenerationStartMessage(prompt, styleDisplay);

                    return telegramBotSessionService.updateWaitingStyle(userId, WAITING_STYLE_NONE)
                            .then(sendMessage(chatId, message))
                            .then(generateImage(userId, sessionId, prompt, prompt, inputUrls, styleId));
                });
    }

    /**
     * Обработать callback query от inline-кнопок.
     */
    private Mono<Void> handleCallbackQuery(TelegramCallbackQuery callbackQuery) {
        log.info("Обработка callback query: {}", callbackQuery.getId());

        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChat().getId();

        if (data == null) {
            return telegramMessageService.answerCallbackQuery(callbackQuery.getId()).then();
        }

        if (data.startsWith("generate_current:")) {
            return handleGenerateCurrentCallback(data, chatId);
        }
        if (data.startsWith("enhance_prompt:")) {
            return handleEnhancePromptCallback(data, chatId);
        }
        if (data.startsWith("edit_prompt:")) {
            return handleEditPromptCallback(data, chatId);
        }
        if (data.startsWith("change_style:")) {
            return handleChangeStyleCallback(data, chatId);
        }
        if (data.startsWith("style:")) {
            return handleStyleCallback(data, chatId);
        }

        return telegramMessageService.answerCallbackQuery(callbackQuery.getId()).then();
    }

    /**
     * Обработать callback "generate_current".
     */
    private Mono<Void> handleGenerateCurrentCallback(String data, Long chatId) {
        String[] parts = data.split(":", 4);
        if (parts.length < 4) {
            return Mono.empty();
        }

        Long sessionId = Long.parseLong(parts[1]);
        Long userId = Long.parseLong(parts[2]);

        return Mono.zip(
                        artStyleService.getUserStyle(userId),
                        getPromptAndInputUrlsFromDB(userId))
                .flatMap(tuple -> {
                    UserStyle userStyle = tuple.getT1();
                    TelegramBotSession tgSession = tuple.getT2();

                    String prompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                    List<String> inputUrls = parseInputUrls(tgSession.getInputImageUrls());

                    return telegramBotSessionService.clearInputUrls(userId)
                            .then(telegramBotSessionService.updateWaitingStyle(userId, WAITING_STYLE_NONE))
                            .then(generateWithUserStyle(userId, sessionId, chatId, prompt, inputUrls, userStyle));
                })
                .onErrorResume(error -> {
                    log.error("Ошибка при обработке generate_current для userId={}", userId, error);
                    return sendMessage(chatId, "❌ Произошла ошибка при генерации изображения");
                });
    }

    /**
     * Сгенерировать изображение с сохраненным стилем пользователя.
     */
    private Mono<Void> generateWithUserStyle(Long userId, Long sessionId, Long chatId, String prompt,
                                              List<String> inputUrls, UserStyle userStyle) {
        Long styleId = userStyle.getStyleId() != null ? userStyle.getStyleId() : artStyleService.getDefaultUserStyleId();
        return artStyleService.getStyleById(styleId)
                .flatMap(style -> {
                    String message = buildGenerationStartMessage(prompt, style.getName());
                    return sendMessage(chatId, message)
                            .then(generateImage(userId, sessionId, prompt, prompt, inputUrls, styleId));
                });
    }

    /**
     * Обработать callback "enhance_prompt".
     */
    private Mono<Void> handleEnhancePromptCallback(String data, Long chatId) {
        String[] parts = data.split(":", 3);
        if (parts.length < 3) {
            return Mono.empty();
        }

        Long sessionId = Long.parseLong(parts[1]);
        Long userId = Long.parseLong(parts[2]);

        return getPromptAndInputUrlsFromDB(userId)
                .flatMap(tgSession -> {
                    String originalPrompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                    List<String> inputUrls = parseInputUrls(tgSession.getInputImageUrls());

                    if (originalPrompt.trim().isEmpty()) {
                        return sendMessage(chatId, "❌ Промпт пуст. Отправьте промпт для улучшения.");
                    }

                    return sendMessage(chatId, "💡 *Улучшение промпта с помощью ИИ...*\n\n⏱️ *Ожидайте 10-15 секунд*")
                            .then(getUserStyleOrDefault(userId))
                            .flatMap(userStyle -> enhancePromptWithStyle(userId, sessionId, chatId, originalPrompt, inputUrls, userStyle));
                });
    }

    /**
     * Получить стиль пользователя или дефолтный.
     */
    private Mono<UserStyle> getUserStyleOrDefault(Long userId) {
        return artStyleService.getUserStyle(userId)
                .switchIfEmpty(Mono.defer(() -> {
                    return artStyleService.getStyleById(artStyleService.getDefaultUserStyleId())
                            .map(style -> {
                                UserStyle defaultUserStyle = new UserStyle();
                                defaultUserStyle.setUserId(userId);
                                defaultUserStyle.setStyleId(artStyleService.getDefaultUserStyleId());
                                return defaultUserStyle;
                            });
                }));
    }

    /**
     * Улучшить промпт с учетом стиля.
     */
    private Mono<Void> enhancePromptWithStyle(Long userId, Long sessionId, Long chatId, String originalPrompt,
                                               List<String> inputUrls, UserStyle userStyle) {
        Long styleId = userStyle.getStyleId() != null ? userStyle.getStyleId() : artStyleService.getDefaultUserStyleId();
        return artStyleService.getStyleById(styleId)
                .flatMap(style -> promptEnhancementService.enhancePrompt(originalPrompt, inputUrls, style))
                .flatMap(enhancedPrompt -> telegramBotSessionService.updatePromptAndInputUrls(userId, enhancedPrompt, inputUrls)
                        .then(sendMessage(chatId, enhancedPrompt))
                        .then(showStyleSelection(chatId, userId, sessionId, enhancedPrompt, inputUrls)))
                .onErrorResume(error -> {
                    log.error("Ошибка улучшения промпта для userId={}", userId, error);
                    return sendMessage(chatId, "❌ Не удалось улучшить промпт. Попробуйте еще раз или используйте оригинальный промпт.");
                });
    }

    /**
     * Обработать callback "edit_prompt".
     */
    private Mono<Void> handleEditPromptCallback(String data, Long chatId) {
        String[] parts = data.split(":", 3);
        if (parts.length < 3) {
            return Mono.empty();
        }

        Long userId = Long.parseLong(parts[2]);
        return telegramBotSessionService.updateWaitingStyle(userId, WAITING_STYLE_EDIT_PROMPT)
                .then(sendMessage(chatId, buildEditPromptMessage()));
    }

    /**
     * Обработать callback "change_style".
     */
    private Mono<Void> handleChangeStyleCallback(String data, Long chatId) {
        String[] parts = data.split(":", 4);
        if (parts.length < 4) {
            return Mono.empty();
        }

        Long sessionId = Long.parseLong(parts[1]);
        Long userId = Long.parseLong(parts[2]);

        return getPromptAndInputUrlsFromDB(userId)
                .flatMap(tgSession -> {
                    String prompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                    List<String> inputUrls = parseInputUrls(tgSession.getInputImageUrls());
                    return showStyleList(chatId, userId, sessionId, prompt, inputUrls);
                });
    }

    /**
     * Обработать callback "style".
     */
    private Mono<Void> handleStyleCallback(String data, Long chatId) {
        String[] parts = data.split(":", 5);
        if (parts.length < 5) {
            return Mono.empty();
        }

        String styleName = parts[1];
        Long sessionId = Long.parseLong(parts[2]);
        Long userId = Long.parseLong(parts[3]);

        return getPromptAndInputUrlsFromDB(userId)
                .flatMap(tgSession -> {
                    String prompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                    List<String> inputUrls = parseInputUrls(tgSession.getInputImageUrls());

                    telegramBotSessionService.clearInputUrls(userId).subscribe();

                    return artStyleService.getStyleByName(styleName)
                            .switchIfEmpty(artStyleService.getStyleById(artStyleService.getDefaultUserStyleId()))
                            .flatMap(style -> {
                                artStyleService.saveOrUpdateUserStyleById(userId, style.getId()).subscribe();
                                String message = buildGenerationStartMessage(prompt, style.getName());
                                return sendMessage(chatId, message)
                                        .then(generateImage(userId, sessionId, prompt, prompt, inputUrls, style.getId()));
                            });
                });
    }

    /**
     * Проверить, является ли строка числом.
     */
    private boolean isNumeric(String str) {
        try {
            Integer.parseInt(str.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ==================== Построение сообщений ====================

    private String buildHelpMessage() {
        return String.format("""
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
                
                🌐 *Сайт:* %s
                """, generationProperties.getPointsPerImage(), SITE_URL);
    }

    private String buildWelcomeMessage(String username) {
        return String.format("""
                👋 *Добро пожаловать обратно, %s!*
                
                🎨 Ваш аккаунт уже привязан к Telegram.
                Вы можете генерировать изображения прямо здесь!
                
                📝 *Как использовать:*
                • Отправьте текстовое описание
                • Приложите фото + описание
                
                💰 *Стоимость:* %s поинта за 1 изображение
                • Используйте /help для справки
                """, username, generationProperties.getPointsPerImage());
    }

    private Mono<Void> sendWelcomeMessage(Long chatId, String username) {
        return sendMessage(chatId, buildWelcomeMessage(username));
    }

    private String buildBalanceMessage(Integer points) {
        return String.format("""
                💰 *Ваш баланс поинтов*
                
                🔢 *Текущий баланс:* %d поинтов
                🎨 *Доступно генераций:* %d
                
                💳 *Пополнить баланс:* %s
                """, points, points / generationProperties.getPointsPerImage(), PRICING_URL);
    }

    private String buildInsufficientPointsMessage(Integer points) {
        return String.format("""
                ❌ *Недостаточно поинтов*
                
                💰 *Текущий баланс:* %s поинтов
                🎨 *Требуется:* %s поинтов для генерации
                
                💳 *Пополнить баланс:* %s
                """, points, generationProperties.getPointsPerImage(), PRICING_URL);
    }

    private String buildUserNotFoundMessage() {
        return "❌ Пользователь не найден. Используйте /start для регистрации.";
    }

    private String buildPromptUpdatedMessage(String prompt) {
        return String.format("""
                ✅ *Промпт обновлен!*
                
                📝 *Новый промпт:* %s
                """, prompt);
    }

    private String buildImageGeneratedCaption(String displayPrompt) {
        return String.format("""
                🎨 *Изображение сгенерировано!*
                
                📝 *Промпт:* %s
                💰 *Стоимость:* %s поинта
                
                🔄 *Хотите еще?* Отправьте новое описание для генерации!
                
                ✏️ *Редактировать изображение?* Ответьте на это сообщение с новым промптом
                """, displayPrompt, generationProperties.getPointsPerImage());
    }

    private String buildGenerationErrorMessage() {
        return """
                ❌ *Ошибка генерации*
                Произошла ошибка при создании изображения. Попробуйте еще раз.""";
    }

    private String buildErrorMessage() {
        return String.format("""
                ❌ *Произошла ошибка*
                
                Попробуйте еще раз или обратитесь в поддержку: %s
                """, SUPPORT_URL);
    }

    private String buildPhotoErrorMessage() {
        return "❌ *Ошибка загрузки фото*\n\nНе удалось обработать изображение. Попробуйте еще раз.";
    }

    private String buildUnknownMessageTypeMessage() {
        return """
                🤔 *Не понимаю*
                
                Отправьте текстовое описание для генерации изображения или фото с подписью.
                """;
    }

    private String buildOldMessageReplyMessage() {
        return "❌ *Нельзя ответить на старое сообщение*\n\nОтвечайте только на последнее сгенерированное изображение.";
    }

    private String buildEmptyPromptMessage() {
        return "❌ *Пустой запрос*\n\nОтправьте текстовое описание для изменения изображения.";
    }

    private String buildUnknownCommandMessage(String command) {
        return String.format("""
                ❓ *Неизвестная команда*
                
                🤖 *Команда:* %s
                📋 *Доступные команды:*
                • /start - Начать работу с ботом
                • /balance - Баланс поинтов
                • /help - Справка
                
                💡 *Или просто отправьте описание изображения для генерации!*
                """, command);
    }

    private String buildStyleSelectionMessage(String styleName) {
        return String.format("""
                💡 *Текущий стиль:* %s
                
                🎨 *Выберите действие:*
                """, styleName);
    }

    private String buildStyleSelectionKeyboard(Long sessionId, Long userId) {
        return String.format("""
                {
                    "inline_keyboard": [
                        [{"text": "💡 Улучшить промпт с помощью ИИ", "callback_data": "enhance_prompt:%d:%d"}],
                        [{"text": "✏️ Редактировать промпт", "callback_data": "edit_prompt:%d:%d"}],
                        [{"text": "🎨 Генерировать с текущим стилем", "callback_data": "generate_current:%d:%d:1"}],
                        [{"text": "🔄 Сменить стиль", "callback_data": "change_style:%d:%d:1"}]
                    ]
                }
                """, sessionId, userId, sessionId, userId, sessionId, userId, sessionId, userId);
    }

    private String buildStyleListMessage(String prompt, List<String> inputImageUrls, List<ArtStyle> allStyles) {
        StringBuilder styleList = new StringBuilder();
        styleList.append("🎨 *Выберите стиль для генерации:*\n\n");
        styleList.append("📝 *Промпт:* ").append(prompt).append("\n\n");
        if (!inputImageUrls.isEmpty()) {
            styleList.append("🖼️ *Референс:* загружен\n\n");
        }
        styleList.append("💡 *Введите номер стиля:*\n\n");

        int index = 1;
        for (ArtStyle style : allStyles) {
            String emoji = style.getName().equals(DEFAULT_STYLE_NAME) ? "⚪" : "🎨";
            styleList.append(index).append(". ").append(emoji).append(" ").append(style.getName()).append("\n");
            index++;
        }
        styleList.append("\nПример: отправьте *1* для выбора без стиля");
        return styleList.toString();
    }

    private String buildGenerationStartMessage(String prompt, String styleDisplay) {
        return String.format("""
                🎨 *Генерация изображения*
                
                📝 *Промпт:* %s
                
                🎨 *Стиль:* %s
                
                ⏱️ *Ожидайте 5-10 секунд*
                """, prompt, styleDisplay);
    }

    private String buildEditPromptMessage() {
        return """
                ✏️ *Редактирование промпта*
                
                📝 Отправьте новый текст промпта для замены текущего.
                
                💡 Вы можете скопировать и скорректировать улучшенный промпт или написать свой.
                """;
    }
}
