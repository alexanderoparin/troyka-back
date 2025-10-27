package ru.oparin.troyka.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.generation")
public class GenerationProperties {

    /**
     * Количество поинтов, которые списываются за одно сгенерированное изображение
     */
    private Integer pointsPerImage = 2;
    
    /**
     * Количество поинтов, которые начисляются пользователю при регистрации
     */
    private Integer pointsOnRegistration = 4;
}

