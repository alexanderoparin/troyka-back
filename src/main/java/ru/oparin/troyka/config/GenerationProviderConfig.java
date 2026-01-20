package ru.oparin.troyka.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.service.provider.FalAIProvider;
import ru.oparin.troyka.service.provider.ImageGenerationProvider;
import ru.oparin.troyka.service.provider.LaoZhangProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Конфигурация для регистрации провайдеров генерации изображений.
 */
@Configuration
@RequiredArgsConstructor
public class GenerationProviderConfig {

    private final FalAIProvider falAIProvider;
    private final LaoZhangProvider laoZhangProvider;

    /**
     * Создать Map всех доступных провайдеров.
     *
     * @return Map провайдеров
     */
    @Bean
    public Map<GenerationProvider, ImageGenerationProvider> generationProviders() {
        Map<GenerationProvider, ImageGenerationProvider> providers = new HashMap<>();
        providers.put(GenerationProvider.FAL_AI, falAIProvider);
        providers.put(GenerationProvider.LAOZHANG_AI, laoZhangProvider);
        return providers;
    }
}
