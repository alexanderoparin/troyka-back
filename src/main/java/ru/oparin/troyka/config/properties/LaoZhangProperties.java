package ru.oparin.troyka.config.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурационные свойства для интеграции с LaoZhang AI API.
 * Настройки загружаются из application.yml с префиксом laozhang.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "laozhang")
public class LaoZhangProperties {

    /**
     * Настройки API LaoZhang AI (URL и ключ авторизации).
     */
    private Api api;

    /**
     * Настройки API LaoZhang AI.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api {
        /**
         * Базовый URL API LaoZhang AI.
         */
        private String url;

        /**
         * Альтернативный URL API LaoZhang AI (Cloudflare).
         */
        private String urlCf;

        /**
         * API ключ для авторизации в LaoZhang AI.
         */
        private String key;
    }
}
