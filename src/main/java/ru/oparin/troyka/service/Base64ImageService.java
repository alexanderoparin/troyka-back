package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сервис для работы с base64 изображениями.
 * Декодирует base64 и сохраняет изображения на сервер.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Base64ImageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.host}")
    private String serverHost;

    private static final Pattern BASE64_PATTERN = Pattern.compile("data:image/([^;]+);base64,([A-Za-z0-9+/=]+)");

    /**
     * Сохранить base64 изображение и получить URL.
     *
     * @param base64Data base64 строка (может быть с префиксом data:image/...;base64,)
     * @param username   имя пользователя для логирования
     * @return URL сохраненного изображения
     */
    public Mono<String> saveBase64ImageAndGetUrl(String base64Data, String username) {
        return saveBase64ImageAndGetUrl(base64Data, username, null);
    }

    /**
     * Сохранить base64 изображение в поддиректорию и получить URL.
     *
     * @param base64Data   base64 строка (может быть с префиксом data:image/...;base64,)
     * @param username     имя пользователя для логирования
     * @param subdirectory поддиректория для сохранения (например, "laozhang") или null для корневой директории
     * @return URL сохраненного изображения
     */
    public Mono<String> saveBase64ImageAndGetUrl(String base64Data, String username, String subdirectory) {
        return Mono.fromCallable(() -> {
            try {
                // Извлекаем base64 данные (убираем префикс если есть)
                String base64Content = extractBase64Content(base64Data);
                String imageFormat = extractImageFormat(base64Data);

                // Декодируем base64
                byte[] imageBytes = Base64.getDecoder().decode(base64Content);

                // Определяем путь для сохранения
                Path uploadPath = Paths.get(uploadDir);
                if (subdirectory != null && !subdirectory.isEmpty()) {
                    uploadPath = uploadPath.resolve(subdirectory);
                }

                // Создаем директорию для загрузки, если она не существует
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                // Генерируем уникальное имя файла
                String uniqueFilename = UUID.randomUUID().toString() + "." + imageFormat;

                // Сохраняем файл
                Path filePath = uploadPath.resolve(uniqueFilename);
                Files.write(filePath, imageBytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

                // Валидация размера
                long fileSize = Files.size(filePath);
                long maxFileSize = 10 * 1024 * 1024; // 10MB

                if (fileSize > maxFileSize) {
                    Files.delete(filePath);
                    log.warn("Изображение {} слишком большое ({} байт), удалено", uniqueFilename, fileSize);
                    throw new IllegalArgumentException("Изображение слишком большое (максимум 10MB)");
                }

                if (fileSize == 0) {
                    Files.delete(filePath);
                    log.warn("Пустое изображение {} удалено", uniqueFilename);
                    throw new IllegalArgumentException("Изображение не может быть пустым");
                }

                // Формируем URL с учетом поддиректории
                String urlPath = subdirectory != null && !subdirectory.isEmpty() 
                        ? "/files/" + subdirectory + "/" + uniqueFilename 
                        : "/files/" + uniqueFilename;
                String fileUrl = "https://" + serverHost + urlPath;
                log.info("Base64 изображение успешно сохранено пользователем {}: {} (размер: {} байт)",
                        username, fileUrl, fileSize);
                return fileUrl;

            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.error("Ошибка при сохранении base64 изображения пользователем {}", username, e);
                throw new RuntimeException("Ошибка при сохранении изображения: " + e.getMessage());
            }
        });
    }

    /**
     * Извлечь base64 содержимое из строки (убрать префикс data:image/...;base64,).
     *
     * @param base64Data полная base64 строка
     * @return чистый base64 контент
     */
    private String extractBase64Content(String base64Data) {
        if (base64Data == null || base64Data.isEmpty()) {
            throw new IllegalArgumentException("Base64 данные не могут быть пустыми");
        }

        // Проверяем наличие префикса data:image/...;base64,
        Matcher matcher = BASE64_PATTERN.matcher(base64Data);
        if (matcher.find()) {
            return matcher.group(2);
        }

        // Если префикса нет, возвращаем как есть
        return base64Data.trim();
    }

    /**
     * Извлечь формат изображения из base64 строки.
     *
     * @param base64Data полная base64 строка
     * @return формат изображения (png, jpg, jpeg и т.д.)
     */
    private String extractImageFormat(String base64Data) {
        if (base64Data == null || base64Data.isEmpty()) {
            return "png"; // По умолчанию PNG
        }

        // Проверяем наличие префикса data:image/...;base64,
        Matcher matcher = BASE64_PATTERN.matcher(base64Data);
        if (matcher.find()) {
            String format = matcher.group(1).toLowerCase();
            // Нормализуем формат
            if (format.equals("jpeg")) {
                return "jpg";
            }
            return format;
        }

        // Если префикса нет, пытаемся определить по первым байтам
        // Но для простоты возвращаем PNG по умолчанию
        return "png";
    }
}
