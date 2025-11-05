package ru.oparin.troyka.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.oparin.troyka.model.entity.ArtStyle;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Конфигурация кеширования для приложения.
 */
@Configuration
public class CacheConfig {

    /**
     * Кеш для стилей изображений с TTL 30 минут.
     */
    @Bean
    public Cache<String, List<ArtStyle>> artStylesCache() {
        return Caffeine.newBuilder()
                .maximumSize(1) // Только один список стилей
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
    }
}

