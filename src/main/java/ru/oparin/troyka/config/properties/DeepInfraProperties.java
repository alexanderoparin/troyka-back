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
@ConfigurationProperties(prefix = "deepinfra")
public class DeepInfraProperties {

    private Api api;

    private String model;

    private Integer timeout;

    private Integer maxTokens;

    private Double temperature;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api {
        private String url;
        private String key;
    }
}

