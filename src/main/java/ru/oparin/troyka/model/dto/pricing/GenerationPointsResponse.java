package ru.oparin.troyka.model.dto.pricing;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Стоимость генерации в поинтах (единственный источник тарифов для фронта).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Стоимость генерации в поинтах по моделям и разрешениям")
public class GenerationPointsResponse {

    @Schema(description = "Поинтов за одно изображение (модель nano-banana)")
    private Integer pointsPerImage;

    @Schema(description = "Поинтов за одно изображение (модель nano-banana-pro), ключи: 1K, 2K, 4K")
    private Map<String, Integer> pointsPerImagePro;

    @Schema(description = "Поинтов за одно изображение (модель Seedream 4.5)")
    private Integer pointsPerImageSeedream;

    @Schema(description = "Поинтов, начисляемых при регистрации")
    private Integer pointsOnRegistration;
}
