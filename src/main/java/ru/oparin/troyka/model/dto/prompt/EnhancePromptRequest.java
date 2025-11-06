package ru.oparin.troyka.model.dto.prompt;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для запроса улучшения промпта через DeepInfra API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос для улучшения промпта")
public class EnhancePromptRequest {

    /** Исходный промпт пользователя */
    @NotBlank(message = "Промпт не может быть пустым")
    @Schema(description = "Исходный промпт для улучшения", example = "кот в космосе")
    private String prompt;

    /** Опциональный список URL изображений для анализа */
    @Schema(description = "Список URL изображений для анализа (если есть)", 
            example = "[\"https://example.com/image.jpg\"]")
    private List<String> imageUrls;

    /** Идентификатор стиля изображения для контекста улучшения */
    @Schema(description = "Идентификатор стиля изображения (ссылка на art_styles.id). По умолчанию 1 (Без стиля)", example = "2")
    private Long styleId;
}

