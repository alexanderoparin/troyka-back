package ru.oparin.troyka.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.service.ImageProxyService;

/**
 * Контроллер для проксирования изображений из внешних источников.
 * Предоставляет публичный доступ к изображениям через наш домен для корректной работы download атрибута.
 */
@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
@Slf4j
public class ImageProxyController {

    private final ImageProxyService imageProxyService;

    /**
     * Проксирование изображений из FAL AI и других источников.
     * URL формата: /images/v1/{filePath}
     * Пример: /images/v1/b/rabbit/6BawnGfRSUdfEfZvKqmaK.jpg
     * 
     * @param version версия API (v1 для FAL AI)
     * @return изображение с правильными заголовками
     */
    @GetMapping("/{version}/**")
    public Mono<ResponseEntity<byte[]>> proxyImage(@PathVariable String version,
                                                     ServerHttpRequest request) {
        log.info("Запрос проксирования изображения: version={}", version);
        
        return imageProxyService.proxyImage(version, request.getURI().getPath())
                .map(fileData -> {
                    String contentType = determineContentTypeFromData(fileData);
                    
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(contentType))
                            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400") // Кэшируем на сутки
                            .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*") // CORS
                            .header(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET")
                            .body(fileData);
                })
                .onErrorResume(error -> {
                    log.error("Ошибка проксирования изображения: {}", error.getMessage());
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    /**
     * Определить Content-Type по содержимому файла (магические байты).
     */
    private String determineContentTypeFromData(byte[] data) {
        if (data.length < 2) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        
        // JPEG: FF D8
        if (data[0] == (byte)0xFF && data[1] == (byte)0xD8) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        
        // PNG: 89 50 4E 47
        if (data.length >= 4 && data[0] == (byte)0x89 && data[1] == 0x50 && 
            data[2] == 0x4E && data[3] == 0x47) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        
        // GIF: 47 49 46 38
        if (data.length >= 4 && data[0] == 0x47 && data[1] == 0x49 && 
            data[2] == 0x46 && data[3] == 0x38) {
            return MediaType.IMAGE_GIF_VALUE;
        }
        
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}

