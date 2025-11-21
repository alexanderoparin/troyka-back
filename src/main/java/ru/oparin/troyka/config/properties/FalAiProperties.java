package ru.oparin.troyka.config.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурационные свойства для интеграции с Fal.ai API.
 * Настройки загружаются из application.yml с префиксом fal.ai.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "fal.ai")
public class FalAiProperties {

    /**
     * Настройки API Fal.ai (URL и ключ авторизации).
     */
    private Api api;

    /**
     * Настройки моделей Fal.ai (для создания и редактирования изображений).
     */
    private Model model;

    /**
     * Настройки проверки здоровья Fal.ai API.
     */
    private HealthCheck healthCheck;

    /**
     * Настройки системы очередей Fal.ai.
     */
    private Queue queue;

    /**
     * Настройки API Fal.ai.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api  {
        /**
         * Базовый URL API Fal.ai.
         */
        private String url;

        /**
         * API ключ для авторизации в Fal.ai.
         */
        private String key;
    }

    /**
     * Настройки моделей Fal.ai.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Model  {
        /**
         * Имя модели для создания изображений.
         */
        private String create;

        /**
         * Имя модели для редактирования изображений.
         */
        private String edit;
    }

    /**
     * Настройки проверки здоровья Fal.ai API.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthCheck {
        /**
         * Включена ли автоматическая проверка здоровья API.
         */
        private boolean enabled;

        /**
         * Интервал проверки здоровья в миллисекундах.
         */
        private long intervalMs;

        /**
         * Количество повторных попыток при неудачной проверке.
         */
        private int retryCount;

        /**
         * Задержка между повторными попытками в миллисекундах.
         */
        private long retryDelayMs;

        /**
         * Таймаут запроса проверки здоровья в миллисекундах.
         */
        private long timeoutMs;
    }

    /**
     * Настройки системы очередей Fal.ai.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Queue {
        /**
         * Включена ли система очередей для генерации изображений.
         */
        private boolean enabled;

        /**
         * Интервал опроса статуса запросов в очереди в миллисекундах.
         */
        private long pollingIntervalMs;

        /**
         * Максимальное время ожидания завершения запроса в очереди в миллисекундах.
         */
        private long maxWaitTimeMs;
    }
}
