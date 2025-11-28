package ru.oparin.troyka.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.telegram.TelegramCallbackQuery;
import ru.oparin.troyka.model.entity.TelegramBotSession;
import ru.oparin.troyka.model.entity.UserStyle;
import ru.oparin.troyka.service.ArtStyleService;
import ru.oparin.troyka.service.PromptEnhancementService;

import java.util.List;

/**
 * Обработчик callback queries от inline-кнопок Telegram бота.
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class TelegramBotCallbackHandler {

    private static final int WAITING_STYLE_EDIT_PROMPT = -1;

    private final TelegramMessageService telegramMessageService;
    private final TelegramBotSessionService telegramBotSessionService;
    private final ArtStyleService artStyleService;
    private final PromptEnhancementService promptEnhancementService;
    private final TelegramBotMessageBuilder messageBuilder;
    private final TelegramBotStyleHandler styleHandler;
    private final TelegramBotImageGenerator imageGenerator;

    /**
     * Обработать callback query от inline-кнопок.
     */
    public Mono<Void> handleCallbackQuery(TelegramCallbackQuery callbackQuery) {
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
                        telegramBotSessionService.getTelegramBotSessionEntityByUserId(userId))
                .flatMap(tuple -> {
                    UserStyle userStyle = tuple.getT1();
                    TelegramBotSession tgSession = tuple.getT2();

                    String prompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                    List<String> inputUrls = telegramBotSessionService.parseInputUrls(tgSession.getInputImageUrls());

                    return telegramBotSessionService.clearInputUrls(userId)
                            .then(telegramBotSessionService.updateWaitingStyle(userId, 0))
                            .then(generateWithUserStyle(userId, sessionId, chatId, prompt, inputUrls, userStyle));
                })
                .onErrorResume(error -> {
                    log.error("Ошибка при обработке generate_current для userId={}", userId, error);
                    return telegramMessageService.sendMessage(chatId, "❌ Произошла ошибка при генерации изображения");
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
                    String message = messageBuilder.buildGenerationStartMessage(prompt, style.getName());
                    return telegramMessageService.sendMessage(chatId, message)
                            .then(imageGenerator.generateImage(userId, sessionId, prompt, prompt, inputUrls, styleId));
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

        return telegramBotSessionService.getTelegramBotSessionEntityByUserId(userId)
                .flatMap(tgSession -> {
                    String originalPrompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                    List<String> inputUrls = telegramBotSessionService.parseInputUrls(tgSession.getInputImageUrls());

                    if (originalPrompt.trim().isEmpty()) {
                        return telegramMessageService.sendMessage(chatId, "❌ Промпт пуст. Отправьте промпт для улучшения.");
                    }

                    return telegramMessageService.sendMessage(chatId, "💡 *Улучшение промпта с помощью ИИ...*\n\n⏱️ *Ожидайте 10-15 секунд*")
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
                        .then(telegramMessageService.sendMessage(chatId, enhancedPrompt))
                        .then(styleHandler.showStyleSelection(chatId, userId, sessionId, enhancedPrompt, inputUrls)))
                .onErrorResume(error -> {
                    log.error("Ошибка улучшения промпта для userId={}", userId, error);
                    return telegramMessageService.sendMessage(chatId, "❌ Не удалось улучшить промпт. Попробуйте еще раз или используйте оригинальный промпт.");
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
                .then(telegramMessageService.sendMessage(chatId, messageBuilder.buildEditPromptMessage()));
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

        return telegramBotSessionService.getTelegramBotSessionEntityByUserId(userId)
                .flatMap(tgSession -> {
                    String prompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                    List<String> inputUrls = telegramBotSessionService.parseInputUrls(tgSession.getInputImageUrls());
                    return styleHandler.showStyleList(chatId, userId, sessionId, prompt, inputUrls);
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

        return telegramBotSessionService.getTelegramBotSessionEntityByUserId(userId)
                .flatMap(tgSession -> {
                    String prompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                    List<String> inputUrls = telegramBotSessionService.parseInputUrls(tgSession.getInputImageUrls());

                    telegramBotSessionService.clearInputUrls(userId).subscribe();

                    return artStyleService.getStyleByName(styleName)
                            .switchIfEmpty(artStyleService.getStyleById(artStyleService.getDefaultUserStyleId()))
                            .flatMap(style -> {
                                artStyleService.saveOrUpdateUserStyleById(userId, style.getId()).subscribe();
                                String message = messageBuilder.buildGenerationStartMessage(prompt, style.getName());
                                return telegramMessageService.sendMessage(chatId, message)
                                        .then(imageGenerator.generateImage(userId, sessionId, prompt, prompt, inputUrls, style.getId()));
                            });
                });
    }
}

