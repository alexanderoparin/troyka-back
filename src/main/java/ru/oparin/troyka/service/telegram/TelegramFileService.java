package ru.oparin.troyka.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.telegram.TelegramFileResponse;

import java.time.Duration;

/**
 * Сервис для работы с файлами Telegram Bot API.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class TelegramFileService {

    private final WebClient.Builder webClientBuilder;

    @Value("${telegram.bot.token}")
    private String botToken;

    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * Скачать файл с Telegram.
     *
     * @param fileId ID файла в Telegram
     * @return содержимое файла в виде байтов
     */
    public Mono<byte[]> downloadFile(String fileId) {
        WebClient webClient = webClientBuilder
                .baseUrl(TELEGRAM_API_URL + botToken)
                .build();

        return webClient.get()
                .uri("/getFile?file_id=" + fileId)
                .retrieve()
                .bodyToMono(TelegramFileResponse.class)
                .timeout(TIMEOUT)
                .flatMap(response -> {
                    if (response.isOk() && response.getResult() != null) {
                        String filePath = response.getResult().getFilePath();

                        // Скачиваем файл - используем полный URL
                        String fullFileUrl = "https://api.telegram.org/file/bot" + botToken + "/" + filePath;
                        return webClient.get()
                                .uri(fullFileUrl)
                                .retrieve()
                                .bodyToMono(byte[].class)
                                .doOnSuccess(bytes -> log.info("Файл скачан, размер: {} байт", bytes.length));
                    }
                    return Mono.error(new RuntimeException("Не удалось получить путь к файлу: " + response.getDescription()));
                })
                .doOnError(error -> log.error("Ошибка скачивания файла: {}", error.getMessage()));
    }
}