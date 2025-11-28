package ru.oparin.troyka.model.dto.fal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.Resolution;

import java.util.List;

/**
 * DTO для запроса генерации изображения с помощью FAL AI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос для генерации изображения с помощью промпта от FAL AI")
public class ImageRq {

    /** Идентификатор сессии для сохранения истории генерации */
    @Schema(description = "Идентификатор сессии для сохранения истории генерации. Если не указан, будет создана или получена дефолтная сессия")
    private Long sessionId;

    /** Описание изображения (промпт) */
    @NotBlank(message = "Промпт не может быть пустым")
    @Schema(description = "Описание изображения", example = "инопланетянин в таджикском стиле")
    private String prompt;

    /** Список URL входных изображений для редактирования (отправляются в FAL AI и сохраняются в истории) */
    @Schema(description = "Список URL входных изображений для редактирования. Отправляются в FAL AI и сохраняются в истории сессии",
            example = "[\"https://storage.googleapis.com/falserverless/example_inputs/nano-banana-edit-input.png\"]")
    private List<String> inputImageUrls;

    /** Количество изображений для генерации */
    @Builder.Default
    @Schema(description = "Количество изображений", example = "2")
    private Integer numImages = 1;

    /** Идентификатор стиля изображения */
    @Builder.Default
    @Schema(description = "Идентификатор стиля изображения (ссылка на art_styles.id). По умолчанию 1 (Без стиля)", example = "1")
    private Long styleId = 1L;

    /** Соотношение сторон изображения */
    @Builder.Default
    @Schema(description = "Соотношение сторон изображения. Возможные значения: 21:9, 16:9, 3:2, 4:3, 5:4, 1:1, 4:5, 3:4, 2:3, 9:16. По умолчанию: 1:1", example = "1:1")
    private String aspectRatio = "1:1";

    /** Тип модели для генерации */
    @Builder.Default
    @Schema(description = "Тип модели для генерации. Возможные значения: NANO_BANANA, NANO_BANANA_PRO. По умолчанию: NANO_BANANA", example = "NANO_BANANA")
    private GenerationModelType model = GenerationModelType.NANO_BANANA;

    /** Разрешение изображения */
    @Schema(description = "Разрешение изображения. Возможные значения: RESOLUTION_1K, RESOLUTION_2K, RESOLUTION_4K", example = "RESOLUTION_1K")
    private Resolution resolution;
}
