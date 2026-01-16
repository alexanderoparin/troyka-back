package ru.oparin.troyka.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.model.dto.telegram.TelegramMessage;
import ru.oparin.troyka.model.dto.telegram.TelegramPhoto;
import ru.oparin.troyka.model.dto.telegram.TelegramUpdate;
import ru.oparin.troyka.model.dto.telegram.TelegramUser;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.service.ImageGenerationHistoryService;
import ru.oparin.troyka.service.PricingService;
import ru.oparin.troyka.service.UserPointsService;
import ru.oparin.troyka.service.UserService;

import java.util.List;

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
    private static final String TELEGRAM_PROXY_URL_PREFIX = "https://24reshai.ru/api/telegram/proxy/";

    private final UserService userService;
    private final TelegramBotSessionService telegramBotSessionService;
    private final UserPointsService userPointsService;
    private final TelegramMessageService telegramMessageService;
    private final ImageGenerationHistoryService imageGenerationHistoryService;
    private final GenerationProperties generationProperties;
    private final TelegramBotMessageBuilder messageBuilder;
    private final TelegramBotCallbackHandler callbackHandler;
    private final TelegramBotStyleHandler styleHandler;
    private final TelegramBotImageGenerator imageGenerator;
    private final PricingService pricingService;

    /**
     * Обработать команду /start.
     */
    public Mono<Void> handleStartCommand(Long chatId, Long telegramId, String username, String firstName, String lastName) {
        log.info("Обработка команды /start для чата {} и пользователя {}", chatId, telegramId);

        return findOrCreateUser(telegramId, username, firstName, lastName, chatId)
                .flatMap(user -> sendMessage(chatId, messageBuilder.buildWelcomeMessage(user.getUsername())))
                .then()
                .doOnSuccess(v -> log.info("Команда /start обработана для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки команды /start для чата {}", chatId, error));
    }

    /**
     * Обработать команду /help.
     */
    public Mono<Void> handleHelpCommand(Long chatId) {
        log.info("Обработка команды /help для чата {}", chatId);
        return sendMessage(chatId, messageBuilder.buildHelpMessage())
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
                        .flatMap(points -> {
                            String message = messageBuilder.buildBalanceMessageWithTopUp(points);
                            String keyboard = messageBuilder.buildBalanceKeyboard(points);
                            return telegramMessageService.sendMessageWithKeyboard(chatId, message, keyboard);
                        }))
                .doOnSuccess(v -> log.info("Команда /balance обработана для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки команды /balance для чата {}", chatId, error));
    }

    /**
     * Обработать команду /buy.
     */
    public Mono<Void> handleBuyCommand(Long chatId, Long telegramId) {
        log.info("Обработка команды /buy для чата {} и пользователя {}", chatId, telegramId);

        return findUserByTelegramId(telegramId, chatId)
                .flatMap(user -> pricingService.getActivePricingPlans()
                        .collectList()
                        .flatMap(plans -> {
                            if (plans.isEmpty()) {
                                return sendMessage(chatId, "❌ Тарифные планы временно недоступны. Попробуйте позже.");
                            }
                            String message = messageBuilder.buildPricingPlansMessage(plans);
                            String keyboard = messageBuilder.buildPricingPlansKeyboard(plans);
                            return telegramMessageService.sendMessageWithKeyboard(chatId, message, keyboard);
                        }))
                .doOnSuccess(v -> log.info("Команда /buy обработана для чата {}", chatId))
                .doOnError(error -> log.error("Ошибка обработки команды /buy для чата {}", chatId, error));
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
                    .then(callbackHandler.handleCallbackQuery(update.getCallbackQuery()));
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
                    return sendMessage(chatId, messageBuilder.buildErrorMessage());
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
                .switchIfEmpty(Mono.defer(() -> sendMessage(chatId, messageBuilder.buildUserNotFoundMessage())
                        .then(Mono.empty())));
    }

    /**
     * Проверить баланс и обработать запрос.
     */
    private Mono<Void> checkBalanceAndProcess(User user, Long chatId, String prompt, List<String> inputImageUrls) {
        return userPointsService.getUserPoints(user.getId())
                .flatMap(points -> {
                    if (points < generationProperties.getPointsPerImage()) {
                        String message = messageBuilder.buildInsufficientPointsMessageWithTopUp(points);
                        String keyboard = messageBuilder.buildInsufficientPointsKeyboard();
                        return telegramMessageService.sendMessageWithKeyboard(chatId, message, keyboard);
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

        return styleHandler.showStyleSelection(chatId, user.getId(), sessionId, prompt, inputImageUrls);
    }

    /**
     * Обработать редактирование промпта.
     */
    private Mono<Void> handlePromptEdit(Long userId, Long chatId, Long sessionId, String prompt, List<String> inputImageUrls) {
        log.debug("Редактирование промпта для userId={}", userId);
        return telegramBotSessionService.updatePromptAndInputUrls(userId, prompt, inputImageUrls)
                .then(telegramBotSessionService.updateWaitingStyle(userId, WAITING_STYLE_NONE))
                .then(sendMessage(chatId, messageBuilder.buildPromptUpdatedMessage(prompt)))
                .then(styleHandler.showStyleSelection(chatId, userId, sessionId, prompt, inputImageUrls));
    }

    /**
     * Обработать ввод номера стиля.
     */
    private Mono<Void> handleStyleNumberInput(Long chatId, Long userId, Long sessionId, String prompt, Integer waitingStyle) {
        if (isNumeric(prompt)) {
            log.debug("Переход в handleStyleSelection");
            return styleHandler.handleStyleSelection(chatId, userId, sessionId, prompt)
                    .flatMap(result -> result != null
                            ? imageGenerator.generateImage(userId, sessionId, result.getPrompt(),
                                    result.getPrompt(), result.getInputUrls(), result.getStyleId())
                            : Mono.empty());
        } else {
            log.debug("Ввод не является цифрой, сбрасываем waitingStyle и показываем выбор стиля");
            return telegramBotSessionService.updateWaitingStyle(userId, WAITING_STYLE_NONE)
                    .then(telegramBotSessionService.getTelegramBotSessionEntityByUserId(userId))
                    .flatMap(tgSession -> styleHandler.showStyleSelection(chatId, userId, sessionId, prompt,
                            telegramBotSessionService.parseInputUrls(tgSession.getInputImageUrls())));
        }
    }

    /**
     * Проверить баланс и показать выбор стиля.
     */
    private Mono<Void> checkBalanceAndShowStyleSelection(User user, Long chatId, String prompt, List<String> inputImageUrls) {
        return userPointsService.getUserPoints(user.getId())
                .flatMap(points -> {
                    if (points < generationProperties.getPointsPerImage()) {
                        String message = messageBuilder.buildInsufficientPointsMessageWithTopUp(points);
                        String keyboard = messageBuilder.buildInsufficientPointsKeyboard();
                        return telegramMessageService.sendMessageWithKeyboard(chatId, message, keyboard);
                    }
                    return telegramBotSessionService.getOrCreateTelegramBotSession(user.getId(), chatId)
                            .flatMap(session -> styleHandler.showStyleSelection(chatId, user.getId(), session.getId(), prompt, inputImageUrls));
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
                        return sendMessage(chatId, messageBuilder.buildPhotoErrorMessage());
                    });
        }

        if (message.getText() != null && !message.getText().trim().isEmpty()) {
            return handleTextMessage(chatId, telegramId, message.getText());
        }

        log.debug("Получено сообщение неизвестного типа от пользователя {} в чате {}", telegramId, chatId);
        return sendMessage(chatId, messageBuilder.buildUnknownMessageTypeMessage());
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
                                return sendMessage(chatId, messageBuilder.buildOldMessageReplyMessage());
                            }

                            return processImageEdit(user, chatId, message.getText());
                        }));
    }

    /**
     * Обработать редактирование изображения.
     */
    private Mono<Void> processImageEdit(User user, Long chatId, String newPrompt) {
        if (newPrompt == null || newPrompt.trim().isEmpty()) {
            return sendMessage(chatId, messageBuilder.buildEmptyPromptMessage());
        }

        String displayPrompt = String.format("<исходное изображение> %s", newPrompt);
        log.info("Диалог с изображением: пользователь {} изменил промпт на '{}'", user.getId(), displayPrompt);

        return telegramBotSessionService.getTelegramBotSessionByUserId(user.getId())
                .flatMap(session -> imageGenerationHistoryService.getLastGeneratedImageUrlFromSession(
                        user.getId(), session.getId()))
                .flatMap(previousImageUrl -> telegramBotSessionService.getOrCreateTelegramBotSession(user.getId(), chatId)
                        .flatMap(session -> styleHandler.showStyleSelection(chatId, user.getId(), session.getId(),
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
            case "/buy" -> handleBuyCommand(chatId, userId);
            default -> handleUnknownCommand(chatId, command);
        };
    }

    /**
     * Обработать неизвестную команду.
     */
    private Mono<Void> handleUnknownCommand(Long chatId, String command) {
        log.info("Получена неизвестная команда: {} в чате {}", command, chatId);
        return sendMessage(chatId, messageBuilder.buildUnknownCommandMessage(command));
    }

    /**
     * Отправить текстовое сообщение.
     */
    public Mono<Void> sendMessage(Long chatId, String message) {
        return telegramMessageService.sendMessage(chatId, message);
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

}
