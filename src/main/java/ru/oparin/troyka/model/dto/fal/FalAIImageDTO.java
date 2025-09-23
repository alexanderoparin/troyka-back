package ru.oparin.troyka.model.dto.fal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Изображение, сгенерированное FAL AI")
public class FalAIImageDTO {
    @Schema(description = "URL сгенерированного изображения", example = "https://v3.fal.media/files/kangaroo/xMO087rdsATkwF1VR2bRV.jpeg")
    private String url;

    @Schema(description = "Тип содержимого изображения", example = "image/jpeg")
    private String contentType;

    @Schema(description = "Имя файла изображения", example = "output.jpeg")
    private String fileName;

    @Schema(description = "Размер файла изображения", example = "null")
    private Long fileSize;
}