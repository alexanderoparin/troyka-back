package ru.oparin.troyka.model.dto.laozhang;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для запроса к LaoZhang AI API.
 * Использует формат Google Gemini API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaoZhangRequestDTO {

    /**
     * Содержимое запроса в формате Gemini API.
     */
    private List<Content> contents;

    /**
     * Конфигурация генерации.
     */
    @JsonProperty("generationConfig")
    private GenerationConfig generationConfig;

    /**
     * Содержимое запроса в формате Gemini API.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {
        /**
         * Части содержимого (текст и/или изображения).
         */
        private List<Part> parts;
    }

    /**
     * Часть содержимого (текст или изображение).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Part {
        /**
         * Текст (для текстовых частей).
         */
        private String text;

        /**
         * Встроенное изображение (для изображений в base64).
         */
        @JsonProperty("inlineData")
        private InlineData inlineData;
    }

    /**
     * Встроенные данные изображения в base64.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InlineData {
        /**
         * MIME тип изображения.
         */
        @JsonProperty("mimeType")
        private String mimeType;

        /**
         * Base64 данные изображения (без префикса data:image/...;base64,).
         */
        private String data;
    }

    /**
     * Конфигурация генерации.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationConfig {
        /**
         * Модальности ответа (всегда ["IMAGE"] для генерации изображений).
         */
        @JsonProperty("responseModalities")
        private List<String> responseModalities;

        /**
         * Конфигурация изображения (для Pro версии).
         */
        @JsonProperty("imageConfig")
        private ImageConfig imageConfig;
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
        @JsonProperty("imageSize")
        private String imageSize;

        /**
         * Соотношение сторон (1:1, 16:9, 9:16 и т.д.).
         */
        @JsonProperty("aspectRatio")
        private String aspectRatio;
    }
}
