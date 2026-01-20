package ru.oparin.troyka.model.dto.laozhang;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для запроса к LaoZhang AI API.
 * Использует формат OpenAI chat completions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaoZhangRequestDTO {

    /**
     * Модель для генерации.
     * gemini-2.5-flash-image-preview (Standard) или gemini-3-pro-image-preview (Pro)
     */
    private String model;

    /**
     * Отключить streaming (всегда false для синхронных запросов).
     */
    @Builder.Default
    private Boolean stream = false;

    /**
     * Массив сообщений в формате OpenAI.
     */
    private List<Message> messages;

    /**
     * Конфигурация изображения (для Pro версии).
     */
    @JsonProperty("image_config")
    private ImageConfig imageConfig;

    /**
     * Сообщение в формате OpenAI.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        /**
         * Роль отправителя (user или system).
         */
        private String role;

        /**
         * Содержимое сообщения.
         * Может быть строкой или массивом ContentPart.
         */
        private Object content;
    }

    /**
     * Часть содержимого сообщения.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentPart {
        /**
         * Тип части (text или image_url).
         */
        private String type;

        /**
         * Текст (для type="text").
         */
        private String text;

        /**
         * URL изображения (для type="image_url").
         */
        @JsonProperty("image_url")
        private ImageUrl imageUrl;
    }

    /**
     * URL изображения.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageUrl {
        /**
         * URL изображения (может быть data URL с base64).
         */
        private String url;
    }

    /**
     * Конфигурация изображения (для Pro версии).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageConfig {
        /**
         * Размер изображения (1K, 2K, 4K).
         */
        @JsonProperty("image_size")
        private String imageSize;

        /**
         * Соотношение сторон (1:1, 16:9, 9:16 и т.д.).
         */
        @JsonProperty("aspect_ratio")
        private String aspectRatio;
    }
}
