package ru.oparin.troyka.config.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "fal.ai")
public class FalAiProperties {

    private Api api;

    private String model;

    private Integer timeout;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api  {
        private String url;
        private String key;
    }
}
