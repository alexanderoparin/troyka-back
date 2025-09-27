package ru.oparin.troyka.model.dto.fal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос для генерации изображения с помощью промпта от FAL AI")
public class ImageRq {

    @NotBlank
    @Schema(description = "Описание изображения", example = "инопланетянин в таджикском стиле")
    private String prompt;

    @Schema(description = "Список URL изображений для редактирования",
            example = "[\"https://storage.googleapis.com/falserverless/example_inputs/nano-banana-edit-input.png\"]")
    private List<String> imageUrls;

    @Schema(description = "Количество изображений", example = "2")
    private Integer numImages;

    @Schema(description = "Формат изображений", example = "JPEG")
    private OutputFormatEnum outputFormat;
}
