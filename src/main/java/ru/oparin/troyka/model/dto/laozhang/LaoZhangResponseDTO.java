package ru.oparin.troyka.model.dto.laozhang;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для ответа от LaoZhang AI API.
 * Использует формат Google Gemini API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LaoZhangResponseDTO {

    /**
     * Кандидаты (candidates) с результатами генерации.
     */
    private List<Candidate> candidates;

    /**
     * Кандидат с результатом генерации.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        /**
         * Содержимое ответа.
         */
        private Content content;

        /**
         * Причина завершения.
         */
        @JsonProperty("finishReason")
        private String finishReason;
    }

    /**
     * Содержимое ответа.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {
        /**
         * Части содержимого (изображения и/или текст).
         */
        private List<Part> parts;
    }

    /**
     * Часть содержимого.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Part {
        /**
         * Текст (если есть).
         */
        private String text;

        /**
         * Встроенные данные изображения в base64.
         */
        @JsonProperty("inlineData")
        private InlineData inlineData;
    }

    /**
     * Встроенные данные изображения в base64.
     */
    @Data
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
}
