package ru.oparin.troyka.model.dto.fal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для запроса генерации изображения с помощью FAL AI.
 * Поддерживает работу с сессиями для организации истории генераций.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос для генерации изображения с помощью промпта от FAL AI")
public class ImageRq {

    /** Описание изображения (промпт) */
    @NotBlank(message = "Промпт не может быть пустым")
    @Schema(description = "Описание изображения", example = "инопланетянин в таджикском стиле")
    private String prompt;

    /** Список URL входных изображений для редактирования (отправляются в FAL AI и сохраняются в истории) */
    @Schema(description = "Список URL входных изображений для редактирования. Отправляются в FAL AI и сохраняются в истории сессии",
            example = "[\"https://storage.googleapis.com/falserverless/example_inputs/nano-banana-edit-input.png\"]")
    private List<String> inputImageUrls;

    /** Количество изображений для генерации */
    @Schema(description = "Количество изображений", example = "2")
    private Integer numImages;

    /** Формат выходных изображений */
    @Schema(description = "Формат изображений", example = "JPEG")
    private OutputFormatEnum outputFormat;

    /** Идентификатор сессии для сохранения истории генерации */
    @Schema(description = "Идентификатор сессии для сохранения истории генерации. Если не указан, будет создана или получена дефолтная сессия")
    private Long sessionId;

    /** Идентификатор стиля изображения */
    @Schema(description = "Идентификатор стиля изображения (ссылка на art_styles.id). По умолчанию 1 (Без стиля)", example = "1")
    private Long styleId;
}
