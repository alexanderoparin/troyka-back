package ru.oparin.troyka.model.dto.fal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ответ с изображением от FAL AI")
public class ImageRs {
    @Schema(description = "URL сгенерированного изображения", example = "https://v3.fal.media/files/kangaroo/xMO087rdsATkwF1VR2bRV.jpeg")
    private List<String> imageUrls;
    
    @Schema(description = "Обновленный баланс поинтов пользователя после генерации")
    private Integer balance;
}