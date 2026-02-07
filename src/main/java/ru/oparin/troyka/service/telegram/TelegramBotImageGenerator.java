package ru.oparin.troyka.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.service.provider.GenerationProviderRouter;

import java.util.List;

/**
 * Генератор изображений для Telegram бота.
 * Использует активного провайдера из настроек (как и веб), а не только FAL.
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class TelegramBotImageGenerator {

    private static final long DEFAULT_STYLE_ID = 1L;

    private final GenerationProviderRouter providerRouter;
    private final TelegramBotSessionService telegramBotSessionService;
    private final TelegramMessageService telegramMessageService;
    private final TelegramBotMessageBuilder messageBuilder;

    /**
     * Генерировать изображение с указанным стилем.
     */
    public Mono<Void> generateImage(Long userId, Long sessionId, String prompt, String displayPrompt,
                                     List<String> inputImageUrls, Long styleId) {
        log.info("Генерация изображения для пользователя {} в сессии {} с промптом: {} и styleId: {}",
                userId, sessionId, prompt, styleId);

        Long finalStyleId = styleId != null ? styleId : DEFAULT_STYLE_ID;
        ImageRq imageRq = buildImageRequest(prompt, sessionId, inputImageUrls, finalStyleId);

        return providerRouter.generateImage(imageRq, userId)
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

                    String caption = messageBuilder.buildImageGeneratedCaption(displayPrompt);
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
                    return telegramMessageService.sendMessage(chatId, messageBuilder.buildGenerationErrorMessage());
                });
    }
}

