package ru.oparin.troyka.model.dto.fal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос для генерации изображения с помощью промпта от FAL AI")
public class ImageRq {

    @Schema(description = "Описание изображения", example = "инопланетянин в таджикском стиле")
    private String prompt;

    @Schema(description = "Количество изображений", example = "2")
    private Integer numImages;

    @Schema(description = "Формат изображений", example = "jpeg")
    private OutputFormatEnum outputFormat;
}
