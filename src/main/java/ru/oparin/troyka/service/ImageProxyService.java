package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Сервис для проксирования изображений из внешних источников.
 * Поддерживает версионирование API для разных провайдеров.
 * v1 = v3.fal.media
 * v2 = v3b.fal.media
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageProxyService {

    private final WebClient.Builder webClientBuilder;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    
    // Маппинг версий API на базовые URL провайдеров
    private static final Map<String, String> PROVIDER_URLS = Map.of(
        "v1", "https://v3.fal.media",
        "v2", "https://v3b.fal.media"
        // Добавляем новые версии по мере необходимости:
        // "v3", "https://other-provider.com"
    );

    /**
     * Получить изображение из внешнего источника по версии API.
     * 
     * @param version версия API (v1 для FAL AI)
     * @param fullPath полный путь к файлу из запроса
     * @return содержимое изображения в виде байтов
     */
    public Mono<byte[]> proxyImage(String version, String fullPath) {
        // Извлекаем путь после версии (например: /images/v1/b/rabbit/file.jpg -> b/rabbit/file.jpg)
        String filePath = extractFilePath(fullPath, version);
        log.info("Проксирование изображения: version={}, path={}", version, filePath);

        String sourceUrl = buildSourceUrl(version, filePath);
        return webClientBuilder.build()
                .get()
                .uri(sourceUrl)
                .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.parseMediaType("image/*"))
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(TIMEOUT)
                .doOnError(error -> log.error("Ошибка загрузки изображения с URL {}: {}", sourceUrl, error.getMessage()));
    }

    /**
     * Извлечь путь к файлу из полного URI запроса.
     */
    private String extractFilePath(String fullPath, String version) {
        // /api/images/v1/b/rabbit/file.jpg -> b/rabbit/file.jpg (с учетом базового пути /api)
        String prefix = "/images/" + version + "/";
        if (fullPath != null && fullPath.startsWith("/api" + prefix)) {
            // Убираем /api/ префикс
            return fullPath.substring(("/api" + prefix).length());
        } else if (fullPath != null && fullPath.startsWith(prefix)) {
            return fullPath.substring(prefix.length());
        }
        return fullPath != null ? fullPath : "";
    }

    /**
     * Построить URL источника для загрузки изображения.
     * Версии жестко связаны с доменами провайдеров через PROVIDER_URLS.
     */
    private String buildSourceUrl(String version, String filePath) {
        String baseUrl = PROVIDER_URLS.get(version);
        
        if (baseUrl == null) {
            throw new IllegalArgumentException("Unknown API version: " + version);
        }
        
        return baseUrl + "/files/" + filePath;
    }

}

