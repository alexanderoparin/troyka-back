package ru.oparin.troyka.service.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.telegram.TelegramApiResponse;

import java.time.Duration;

/**
 * Сервис для отправки сообщений и медиа через Telegram Bot API.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class TelegramMessageService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${telegram.bot.token}")
    private String botToken;

    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * Отправить текстовое сообщение.
     *
     * @param chatId ID чата
     * @param text текст сообщения
     * @param parseMode режим парсинга (Markdown, HTML)
     * @return результат отправки
     */
    public Mono<Void> sendMessage(Long chatId, String text, String parseMode) {
        log.info("Отправка текстового сообщения в чат {}: {}", chatId, text);

        WebClient webClient = webClientBuilder
                .baseUrl(TELEGRAM_API_URL + botToken)
                .build();

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("chat_id", String.valueOf(chatId));
        body.add("text", text);
        if (parseMode != null) {
            body.add("parse_mode", parseMode);
        }

        return webClient.post()
                .uri("/sendMessage")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(body))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .doOnSuccess(response -> log.info("Сообщение успешно отправлено в чат {}: {}", chatId, response))
                .doOnError(error -> log.error("Ошибка отправки сообщения в чат {}: {}", chatId, error.getMessage()))
                .then();
    }

    /**
     * Отправить текстовое сообщение с Markdown разметкой.
     */
    public Mono<Void> sendMessage(Long chatId, String text) {
        return sendMessage(chatId, text, "Markdown");
    }

    /**
     * Отправить фото с подписью и получить messageId.
     *
     * @param chatId ID чата
     * @param photoUrl URL фото
     * @param caption подпись к фото
     * @return messageId отправленного сообщения
     */
    public Mono<Long> sendPhotoWithMessageId(Long chatId, String photoUrl, String caption) {
        log.info("Отправка фото в чат {}: {}", chatId, photoUrl);

        WebClient webClient = webClientBuilder
                .baseUrl(TELEGRAM_API_URL + botToken)
                .build();

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("chat_id", String.valueOf(chatId));
        body.add("photo", photoUrl);
        if (caption != null && !caption.trim().isEmpty()) {
            body.add("caption", caption);
        }
        body.add("parse_mode", "Markdown");

        return webClient.post()
                .uri("/sendPhoto")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(body))
                .retrieve()
                .bodyToMono(TelegramApiResponse.class)
                .timeout(TIMEOUT)
                .map(response -> {
                    if (response.getOk() && response.getResult() != null) {
                        return response.getResult().getMessageId();
                    } else {
                        log.warn("Telegram API вернул ошибку: {} - {}", response.getErrorCode(), response.getDescription());
                        return 0L;
                    }
                })
                .doOnSuccess(messageId -> log.info("Фото успешно отправлено в чат {} с messageId: {}", chatId, messageId))
                .doOnError(error -> log.error("Ошибка отправки фото в чат {}: {}", chatId, error.getMessage()));
    }

    /**
     * Отправить сообщение "печатает..." (typing indicator).
     *
     * @param chatId ID чата
     * @return результат отправки
     */
    public Mono<Void> sendTypingAction(Long chatId) {
        log.debug("Отправка typing action в чат {}", chatId);

        WebClient webClient = webClientBuilder
                .baseUrl(TELEGRAM_API_URL + botToken)
                .build();

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("chat_id", String.valueOf(chatId));
        body.add("action", "typing");

        return webClient.post()
                .uri("/sendChatAction")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(body))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .doOnError(error -> log.warn("Ошибка отправки typing action в чат {}: {}", chatId, error.getMessage()))
                .then();
    }

    /**
     * Отправить сообщение об ошибке.
     *
     * @param chatId ID чата
     * @param errorMessage сообщение об ошибке
     * @return результат отправки
     */
    public Mono<Void> sendErrorMessage(Long chatId, String errorMessage) {
        String message = "❌ *Ошибка*\n\n" + errorMessage;
        return sendMessage(chatId, message);
    }

    /**
     * Получить сообщение по ID.
     *
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @return сообщение или пустой результат
     */
    public Mono<ru.oparin.troyka.model.dto.telegram.TelegramMessage> getMessageById(Long chatId, Long messageId) {
        log.debug("Получение сообщения {} из чата {}", messageId, chatId);

        WebClient webClient = webClientBuilder
                .baseUrl(TELEGRAM_API_URL + botToken)
                .build();

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("chat_id", String.valueOf(chatId));
        body.add("message_id", String.valueOf(messageId));

        return webClient.post()
                .uri("/getUpdates")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(body))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .map(response -> {
                    // Парсим ответ и извлекаем сообщение
                    // TODO: Реализовать парсинг JSON ответа
                    return (ru.oparin.troyka.model.dto.telegram.TelegramMessage) null; // Временная заглушка
                })
                .doOnError(error -> log.error("Ошибка получения сообщения {} из чата {}: {}", messageId, chatId, error.getMessage()));
    }
}
