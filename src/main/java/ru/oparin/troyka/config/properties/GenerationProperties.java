package ru.oparin.troyka.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.model.enums.Resolution;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.generation")
public class GenerationProperties {

    /** Себестоимость одного изображения LaoZhang: Nano Banana (USD). */
    private static final BigDecimal LAOZHANG_COST_NANO_BANANA_USD = new BigDecimal("0.025");
    /** Себестоимость одного изображения LaoZhang: Pro, любое разрешение (USD). */
    private static final BigDecimal LAOZHANG_COST_PRO_USD = new BigDecimal("0.05");
    /** Себестоимость FAL: Pro 1K/2K (USD). */
    private static final BigDecimal FAL_COST_PRO_1K_2K_USD = new BigDecimal("0.15");
    /** Себестоимость FAL: Pro 4K (USD). */
    private static final BigDecimal FAL_COST_PRO_4K_USD = new BigDecimal("0.30");

    /** Поинтов за одно изображение (модель nano-banana). Источник: application.yml. */
    private Integer pointsPerImage;

    /** Поинты при регистрации. Источник: application.yml. */
    private Integer pointsOnRegistration;

    /** Поинтов за одно изображение PRO по разрешениям (ключи "1K", "2K", "4K"). Источник: application.yml. */
    private Map<String, Integer> pointsPerImagePro;

    /**
     * Получить общую стоимость генерации для указанного количества изображений.
     *
     * @param modelType  тип модели
     * @param resolution разрешение (может быть null)
     * @param numImages  количество изображений
     * @return общее количество поинтов, необходимое для генерации
     */
    public Integer getPointsNeeded(GenerationModelType modelType, Resolution resolution, Integer numImages) {
        return resolvePointsPerImage(modelType, resolution) * numImages;
    }

    /**
     * Вычислить поинты за одно изображение для данной модели и разрешения.
     */
    private Integer resolvePointsPerImage(GenerationModelType modelType, Resolution resolution) {
        if (modelType == GenerationModelType.NANO_BANANA_PRO && resolution != null && pointsPerImagePro != null) {
            String key = resolution.getValue();
            return pointsPerImagePro.getOrDefault(key, pointsPerImagePro.get("1K"));
        }
        return pointsPerImage;
    }

    /**
     * Для API: поинты за одно изображение по разрешениям PRO (ключи "1K", "2K", "4K").
     */
    public Map<String, Integer> getPointsPerImageProForApi() {
        return pointsPerImagePro;
    }

    /**
     * Получить общую себестоимость генерации для указанного количества изображений в долларах США.
     * Использует FAL AI по умолчанию (для обратной совместимости).
     *
     * @param modelType  тип модели
     * @param resolution разрешение (может быть null)
     * @param numImages  количество изображений
     * @return общая себестоимость генерации в долларах США
     */
    public BigDecimal getCostUsd(GenerationModelType modelType, Resolution resolution, Integer numImages) {
        // По умолчанию используем FAL AI (для обратной совместимости)
        return getCostUsd(modelType, resolution, numImages, GenerationProvider.FAL_AI);
    }

    /**
     * Получить общую себестоимость генерации для указанного количества изображений в долларах США.
     *
     * @param modelType  тип модели
     * @param resolution разрешение (может быть null)
     * @param numImages  количество изображений
     * @param provider   провайдер генерации (FAL_AI или LAOZHANG_AI)
     * @return общая себестоимость генерации в долларах США
     */
    public BigDecimal getCostUsd(GenerationModelType modelType, Resolution resolution, Integer numImages, GenerationProvider provider) {
        BigDecimal costPerImage = getCostPerImageUsd(modelType, resolution, provider);
        return costPerImage.multiply(new BigDecimal(numImages));
    }

    /**
     * Получить себестоимость генерации одного изображения в долларах США.
     *
     * @param modelType  тип модели
     * @param resolution разрешение (может быть null)
     * @param provider   провайдер генерации (FAL_AI или LAOZHANG_AI)
     * @return себестоимость одного изображения в долларах США
     */
    private BigDecimal getCostPerImageUsd(GenerationModelType modelType, Resolution resolution, GenerationProvider provider) {
        if (provider == GenerationProvider.LAOZHANG_AI) {
            return modelType == GenerationModelType.NANO_BANANA_PRO ? LAOZHANG_COST_PRO_USD : LAOZHANG_COST_NANO_BANANA_USD;
        }
        if (provider == GenerationProvider.FAL_AI && modelType == GenerationModelType.NANO_BANANA_PRO && resolution != null) {
            return resolution == Resolution.RESOLUTION_4K ? FAL_COST_PRO_4K_USD : FAL_COST_PRO_1K_2K_USD;
        }
        return BigDecimal.ZERO;
    }
}

