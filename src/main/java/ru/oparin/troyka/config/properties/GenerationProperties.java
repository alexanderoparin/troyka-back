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

    /**
     * Поинтов за одно изображение (модель nano-banana). Источник: application.yml.
     */
    private Integer pointsPerImage;

    /**
     * Поинты при регистрации. Источник: application.yml.
     */
    private Integer pointsOnRegistration;

    /**
     * Поинтов за одно изображение PRO по разрешениям (ключи "1K", "2K", "4K"). Источник: application.yml.
     */
    private Map<String, Integer> pointsPerImagePro;

    /**
     * Получить стоимость генерации для модели и разрешения.
     */
    private Integer getPointsPerImage(GenerationModelType modelType, Resolution resolution) {
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
     * Получить общую стоимость генерации для указанного количества изображений.
     *
     * @param modelType  тип модели
     * @param resolution разрешение (может быть null)
     * @param numImages  количество изображений
     * @return общее количество поинтов, необходимое для генерации
     */
    public Integer getPointsNeeded(GenerationModelType modelType, Resolution resolution, Integer numImages) {
        Integer pointsPerImage = getPointsPerImage(modelType, resolution);
        return pointsPerImage * numImages;
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
        // Для LaoZhang AI используются другие цены
        if (provider == GenerationProvider.LAOZHANG_AI) {
            if (modelType == GenerationModelType.NANO_BANANA_PRO) {
                return new BigDecimal("0.05"); // $0.05/image независимо от разрешения
            }
            return new BigDecimal("0.025"); // Nano Banana: $0.025/image
        }

        // Для FAL AI используются текущие цены
        if (modelType == GenerationModelType.NANO_BANANA_PRO && resolution != null) {
            return switch (resolution) {
                case RESOLUTION_1K, RESOLUTION_2K -> new BigDecimal("0.15");
                case RESOLUTION_4K -> new BigDecimal("0.30");
            };
        }
        return BigDecimal.ZERO;
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
}

