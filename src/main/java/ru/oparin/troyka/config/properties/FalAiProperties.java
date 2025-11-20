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

    private Model model;

    private HealthCheck healthCheck;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api  {
        private String url;
        private String key;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Model  {
        private String create;
        private String edit;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthCheck {
        private boolean enabled;
        private long intervalMs;
        private int retryCount;
        private long retryDelayMs;
        private long timeoutMs;
    }
}
