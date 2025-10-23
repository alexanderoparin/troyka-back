package ru.oparin.troyka.service.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
     * Получить URL файла по file_id.
     *
     * @param fileId ID файла в Telegram
     * @return URL файла
     */
    public Mono<String> getFileUrl(String fileId) {
        log.info("Получение URL файла для file_id: {}", fileId);

        WebClient webClient = webClientBuilder
                .baseUrl(TELEGRAM_API_URL + botToken)
                .build();

        return webClient.get()
                .uri("/getFile?file_id=" + fileId)
                .retrieve()
                .bodyToMono(TelegramFileResponse.class)
                .timeout(TIMEOUT)
                .map(response -> {
                    if (response.isOk() && response.getResult() != null) {
                        String filePath = response.getResult().getFilePath();
                        return TELEGRAM_API_URL + botToken + "/" + filePath;
                    }
                    throw new RuntimeException("Не удалось получить URL файла: " + response.getDescription());
                })
                .doOnSuccess(url -> log.info("URL файла получен: {}", url))
                .doOnError(error -> log.error("Ошибка получения URL файла: {}", error.getMessage()));
    }

    /**
     * DTO для ответа от Telegram getFile API.
     */
    public static class TelegramFileResponse {
        private boolean ok;
        private String description;
        private TelegramFile result;

        public boolean isOk() { return ok; }
        public void setOk(boolean ok) { this.ok = ok; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public TelegramFile getResult() { return result; }
        public void setResult(TelegramFile result) { this.result = result; }
    }

    /**
     * DTO для файла Telegram.
     */
    public static class TelegramFile {
        @JsonProperty("file_id")
        private String fileId;
        @JsonProperty("file_unique_id")
        private String fileUniqueId;
        @JsonProperty("file_size")
        private Integer fileSize;
        @JsonProperty("file_path")
        private String filePath;

        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public String getFileUniqueId() { return fileUniqueId; }
        public void setFileUniqueId(String fileUniqueId) { this.fileUniqueId = fileUniqueId; }
        public Integer getFileSize() { return fileSize; }
        public void setFileSize(Integer fileSize) { this.fileSize = fileSize; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
    }
}
