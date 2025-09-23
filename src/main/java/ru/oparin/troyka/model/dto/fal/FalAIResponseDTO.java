package ru.oparin.troyka.model.dto.fal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ответ от FAL AI API")
public class FalAIResponseDTO {
    @Schema(description = "Описание изображения, сгенерированное ИИ", example = "Конечно, вот инопланетянин в таджикском стиле: ")
    private String description;

    @Schema(description = "Список сгенерированных изображений")
    private List<FalAIImageDTO> images;
}