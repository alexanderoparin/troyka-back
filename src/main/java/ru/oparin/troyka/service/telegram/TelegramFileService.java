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
                .doOnNext(response -> {
                    log.info("Ответ от Telegram getFile API: ok={}, description={}", response.isOk(), response.getDescription());
                    if (response.getResult() != null) {
                        log.info("Детали файла: fileId={}, filePath={}, fileSize={}", 
                                response.getResult().getFileId(), 
                                response.getResult().getFilePath(), 
                                response.getResult().getFileSize());
                    }
                })
                .map(response -> {
                    if (response.isOk() && response.getResult() != null) {
                        String filePath = response.getResult().getFilePath();
                        String fullUrl = TELEGRAM_API_URL + botToken + "/" + filePath;
                        log.info("Сформированный URL: {}", fullUrl);
                        return fullUrl;
                    }
                    throw new RuntimeException("Не удалось получить URL файла: " + response.getDescription());
                })
                .doOnSuccess(url -> log.info("URL файла получен: {}", url))
                .doOnError(error -> log.error("Ошибка получения URL файла: {}", error.getMessage()));
    }

    /**
     * Скачать файл с Telegram.
     *
     * @param fileId ID файла в Telegram
     * @return содержимое файла в виде байтов
     */
    public Mono<byte[]> downloadFile(String fileId) {
        log.info("Скачивание файла с Telegram для file_id: {}", fileId);

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
                        log.info("Получен filePath от Telegram: '{}'", filePath);
                        
                        // Скачиваем файл - используем полный URL
                        String fullFileUrl = "https://api.telegram.org/file/bot" + botToken + "/" + filePath;
                        log.info("Скачивание файла по URL: {}", fullFileUrl);
                        
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

    /**
     * Скачать файл с Telegram и получить его содержимое в base64.
     *
     * @param fileId ID файла в Telegram
     * @return содержимое файла в base64
     */
    public Mono<String> downloadFileAsBase64(String fileId) {
        log.info("Скачивание файла с Telegram для file_id: {}", fileId);

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
                        String fileUrl = TELEGRAM_API_URL + botToken + "/" + filePath;
                        
                        // Скачиваем файл
                        return webClient.get()
                                .uri(filePath)
                                .retrieve()
                                .bodyToMono(byte[].class)
                                .map(bytes -> {
                                    String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
                                    log.info("Файл скачан и конвертирован в base64, размер: {} байт", bytes.length);
                                    return base64;
                                });
                    }
                    return Mono.error(new RuntimeException("Не удалось получить путь к файлу: " + response.getDescription()));
                })
                .doOnError(error -> log.error("Ошибка скачивания файла: {}", error.getMessage()));
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
