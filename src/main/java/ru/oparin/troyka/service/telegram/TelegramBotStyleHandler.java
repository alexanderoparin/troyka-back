package ru.oparin.troyka.service.telegram;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.ArtStyle;
import ru.oparin.troyka.model.entity.UserStyle;
import ru.oparin.troyka.service.ArtStyleService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик логики работы со стилями для Telegram бота.
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class TelegramBotStyleHandler {

    private static final long DEFAULT_STYLE_ID = 1L;
    private static final String DEFAULT_STYLE_NAME = "none";

    private final ArtStyleService artStyleService;
    private final TelegramBotSessionService telegramBotSessionService;
    private final TelegramMessageService telegramMessageService;
    private final TelegramBotMessageBuilder messageBuilder;

    // Временное хранилище для списка стилей во время выбора
    private final Map<Long, List<ArtStyle>> sessionStyles = new HashMap<>();

    /**
     * Показать выбор стиля генерации с inline-кнопками.
     */
    public Mono<Void> showStyleSelection(Long chatId, Long userId, Long sessionId, String prompt, List<String> inputImageUrls) {
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
                    String message = messageBuilder.buildStyleSelectionMessage(style.getName());
                    String keyboardJson = messageBuilder.buildStyleSelectionKeyboard(sessionId, userId);
                    return telegramMessageService.sendMessageWithKeyboard(chatId, message, keyboardJson);
                });
    }

    /**
     * Показать пронумерованный список стилей для выбора.
     */
    public Mono<Void> showStyleList(Long chatId, Long userId, Long sessionId, String prompt, List<String> inputImageUrls) {
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

                    String message = messageBuilder.buildStyleListMessage(prompt, inputImageUrls, allStyles);
                    return telegramMessageService.sendMessage(chatId, message);
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
     * Обработать выбор стиля по номеру и вернуть результат для генерации.
     */
    public Mono<StyleSelectionResult> handleStyleSelection(Long chatId, Long userId, Long sessionId, String inputText) {
        log.debug("handleStyleSelection: chatId={}, userId={}, sessionId={}, inputText={}", chatId, userId, sessionId, inputText);

        List<ArtStyle> styles = sessionStyles.get(sessionId);
        if (styles == null || styles.isEmpty()) {
            log.warn("Список стилей не найден для sessionId={}, сбрасываем waitingStyle", sessionId);
            return resetWaitingStyleAndShowSelection(chatId, userId, sessionId)
                    .then(Mono.empty());
        }

        try {
            int styleIndex = Integer.parseInt(inputText.trim());
            if (styleIndex < 1 || styleIndex > styles.size()) {
                return telegramMessageService.sendMessage(chatId, "❌ Неверный номер стиля. Выберите от 1 до " + styles.size())
                        .then(Mono.empty());
            }

            ArtStyle selectedStyle = styles.get(styleIndex - 1);
            return processStyleSelection(chatId, userId, sessionId, selectedStyle);
        } catch (NumberFormatException e) {
            return telegramMessageService.sendMessage(chatId, "❌ Введите номер стиля (цифру)!")
                    .then(Mono.empty());
        }
    }

    /**
     * Сбросить waitingStyle и показать выбор стиля заново.
     */
    private Mono<Void> resetWaitingStyleAndShowSelection(Long chatId, Long userId, Long sessionId) {
        return telegramBotSessionService.updateWaitingStyle(userId, 0)
                .then(telegramBotSessionService.getTelegramBotSessionEntityByUserId(userId))
                .flatMap(tgSession -> {
                    String prompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                    List<String> inputUrls = telegramBotSessionService.parseInputUrls(tgSession.getInputImageUrls());
                    return showStyleSelection(chatId, userId, sessionId, prompt, inputUrls);
                });
    }

    /**
     * Обработать выбранный стиль и вернуть результат для генерации.
     */
    public Mono<StyleSelectionResult> processStyleSelection(Long chatId, Long userId, Long sessionId, ArtStyle selectedStyle) {
        Long styleId = selectedStyle.getId();
        String styleName = selectedStyle.getName();

        return artStyleService.saveOrUpdateUserStyleById(userId, styleId)
                .flatMap(saved -> telegramBotSessionService.getTelegramBotSessionEntityByUserId(userId))
                .map(tgSession -> {
                    String prompt = tgSession.getCurrentPrompt() != null ? tgSession.getCurrentPrompt() : "";
                    List<String> inputUrls = telegramBotSessionService.parseInputUrls(tgSession.getInputImageUrls());

                    sessionStyles.remove(sessionId);
                    String styleDisplay = styleName.equals(DEFAULT_STYLE_NAME) ? "без стиля" : styleName;
                    String message = messageBuilder.buildGenerationStartMessage(prompt, styleDisplay);

                    telegramBotSessionService.updateWaitingStyle(userId, 0).subscribe();
                    telegramMessageService.sendMessage(chatId, message).subscribe();

                    return new StyleSelectionResult(prompt, inputUrls, styleId);
                });
    }

    /**
     * Результат выбора стиля.
     */
    @Getter
    @RequiredArgsConstructor
    public static class StyleSelectionResult {
        private final String prompt;
        private final List<String> inputUrls;
        private final Long styleId;
    }
}

