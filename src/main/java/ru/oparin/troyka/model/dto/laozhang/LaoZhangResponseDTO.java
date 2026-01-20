package ru.oparin.troyka.model.dto.laozhang;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для ответа от LaoZhang AI API.
 * Использует формат OpenAI chat completions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LaoZhangResponseDTO {

    /**
     * ID ответа.
     */
    private String id;

    /**
     * Модель, использованная для генерации.
     */
    private String model;

    /**
     * Массив выборок (choices).
     */
    private List<Choice> choices;

    /**
     * Выборка (choice).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        /**
         * Индекс выборки.
         */
        private Integer index;

        /**
         * Сообщение с результатом.
         */
        private Message message;

        /**
         * Причина завершения.
         */
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    /**
     * Сообщение с результатом.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        /**
         * Роль (обычно "assistant").
         */
        private String role;

        /**
         * Содержимое сообщения (base64 изображение в формате data:image/...;base64,...).
         */
        private String content;
    }
}
