package ru.oparin.troyka.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.Resolution;

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
}

