package ru.oparin.troyka.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.service.telegram.TelegramFileService;

/**
 * Контроллер для проксирования файлов Telegram.
 * Предоставляет публичный доступ к файлам Telegram через наш домен.
 */
@RestController
@RequestMapping("/api/telegram/file")
@RequiredArgsConstructor
@Slf4j
public class TelegramFileProxyController {

    private final TelegramFileService telegramFileService;

    /**
     * Получить файл по file_id от Telegram.
     * Проксирует запрос к Telegram API и возвращает файл.
     *
     * @param fileId ID файла в Telegram
     * @return файл с правильными заголовками
     */
    @GetMapping("/{fileId}")
    public Mono<ResponseEntity<byte[]>> getFile(@PathVariable String fileId) {
        log.info("Проксирование файла с file_id: {}", fileId);

        return telegramFileService.downloadFile(fileId)
                .map(fileData -> {
                    // Определяем Content-Type на основе расширения файла
                    String contentType = getContentType(fileId);
                    
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(contentType))
                            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600") // Кэшируем на 1 час
                            .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*") // CORS для FAL AI
                            .body(fileData);
                })
                .onErrorResume(error -> {
                    log.error("Ошибка проксирования файла {}: {}", fileId, error.getMessage());
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    /**
     * Определить Content-Type файла по file_id.
     * Telegram file_id содержит информацию о типе файла.
     *
     * @param fileId ID файла
     * @return MIME тип
     */
    private String getContentType(String fileId) {
        // Для фото от Telegram обычно возвращаем image/jpeg
        // В реальном проекте можно парсить file_id или запрашивать у Telegram
        if (fileId.startsWith("AgACAgI")) { // Префикс для фото
            return "image/jpeg";
        }
        return "application/octet-stream";
    }
}
