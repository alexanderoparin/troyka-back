package ru.oparin.troyka.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.model.enums.Resolution;

import java.math.BigDecimal;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.generation")
public class GenerationProperties {

    /**
     * Количество поинтов, которые списываются за одно сгенерированное изображение (для старой модели nano-banana)
     */
    private Integer pointsPerImage = 2;
    
    /**
     * Количество поинтов, которые начисляются пользователю при регистрации
     */
    private Integer pointsOnRegistration = 4;

    /**
     * Получить стоимость генерации для модели и разрешения.
     *
     * @param modelType тип модели
     * @param resolution разрешение (может быть null)
     * @return количество поинтов за одно изображение
     */
    private Integer getPointsPerImage(GenerationModelType modelType, Resolution resolution) {
        if (modelType == GenerationModelType.NANO_BANANA_PRO && resolution != null) {
            return switch (resolution) {
                case RESOLUTION_1K -> 8;
                case RESOLUTION_2K -> 9;
                case RESOLUTION_4K -> 15;
            };
        }
        return pointsPerImage; // Для nano-banana всегда 2 поинта
    }

    /**
     * Получить общую стоимость генерации для указанного количества изображений.
     *
     * @param modelType тип модели
     * @param resolution разрешение (может быть null)
     * @param numImages количество изображений
     * @return общее количество поинтов, необходимое для генерации
     */
    public Integer getPointsNeeded(GenerationModelType modelType, Resolution resolution, Integer numImages) {
        Integer pointsPerImage = getPointsPerImage(modelType, resolution);
        return pointsPerImage * numImages;
    }

    /**
     * Получить количество поинтов за одно изображение для обычной модели.
     * 
     * @return количество поинтов за одно изображение
     */
    public Integer getPointsPerImage() {
        return pointsPerImage;
    }

    /**
     * Получить себестоимость генерации одного изображения в долларах США.
     *
     * @param modelType тип модели
     * @param resolution разрешение (может быть null)
     * @param provider провайдер генерации (FAL_AI или LAOZHANG_AI)
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
        return new BigDecimal("0.039"); // Для nano-banana всегда $0.039
    }

    /**
     * Получить общую себестоимость генерации для указанного количества изображений в долларах США.
     * Использует FAL AI по умолчанию (для обратной совместимости).
     *
     * @param modelType тип модели
     * @param resolution разрешение (может быть null)
     * @param numImages количество изображений
     * @return общая себестоимость генерации в долларах США
     */
    public BigDecimal getCostUsd(GenerationModelType modelType, Resolution resolution, Integer numImages) {
        // По умолчанию используем FAL AI (для обратной совместимости)
        return getCostUsd(modelType, resolution, numImages, GenerationProvider.FAL_AI);
    }

    /**
     * Получить общую себестоимость генерации для указанного количества изображений в долларах США.
     *
     * @param modelType тип модели
     * @param resolution разрешение (может быть null)
     * @param numImages количество изображений
     * @param provider провайдер генерации (FAL_AI или LAOZHANG_AI)
     * @return общая себестоимость генерации в долларах США
     */
    public BigDecimal getCostUsd(GenerationModelType modelType, Resolution resolution, Integer numImages, GenerationProvider provider) {
        BigDecimal costPerImage = getCostPerImageUsd(modelType, resolution, provider);
        return costPerImage.multiply(new BigDecimal(numImages));
    }
}

