package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

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
    private static final Duration TIMEOUT = Duration.ofSeconds(120); // Увеличен таймаут для больших изображений
    
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
    /** Число повторных попыток при временном сбое (таймаут, 5xx, сетевая ошибка). */
    private static final int MAX_RETRIES = 2;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(800);

    public Mono<byte[]> proxyImage(String version, String fullPath) {
        String filePath = extractFilePath(fullPath, version);
        String sourceUrl = buildSourceUrl(version, filePath);
        return webClientBuilder.build()
                .get()
                .uri(sourceUrl)
                .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.parseMediaType("image/*"))
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_BACKOFF)
                        .filter(this::isRetryableError)
                        .doBeforeRetry(s -> log.warn("Повтор загрузки изображения (попытка {}): {}", s.totalRetries() + 1, sourceUrl)))
                .doOnError(error -> log.error("Ошибка загрузки изображения с URL {}: {}", sourceUrl, error.getMessage()));
    }

    /** Сбои, при которых имеет смысл повторить запрос (таймаут, сеть, 5xx). */
    private boolean isRetryableError(Throwable error) {
        if (error == null) return false;
        if (error instanceof TimeoutException) return true;
        if (error.getCause() instanceof TimeoutException) return true;
        if (error instanceof WebClientResponseException e && e.getStatusCode().is5xxServerError()) return true;
        String msg = error.getMessage();
        if (msg != null) {
            if (msg.contains("Timeout") || msg.contains("timed out")) return true;
            if (msg.contains("Connection") || msg.contains("connection")) return true;
            if (msg.contains("502") || msg.contains("503") || msg.contains("504")) return true;
        }
        return false;
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

