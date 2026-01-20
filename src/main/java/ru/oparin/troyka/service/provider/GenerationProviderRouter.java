package ru.oparin.troyka.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.dto.fal.ImageRs;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.service.GenerationProviderSettingsService;

import java.util.Map;

/**
 * Роутер для выбора активного провайдера генерации изображений.
 * Маршрутизирует запросы к нужному провайдеру на основе настроек.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationProviderRouter {

    private final GenerationProviderSettingsService settingsService;
    private final Map<GenerationProvider, ImageGenerationProvider> providers;

    /**
     * Генерировать изображение через активного провайдера.
     *
     * @param request запрос на генерацию
     * @param userId  идентификатор пользователя
     * @return ответ с изображениями
     */
    public Mono<ImageRs> generateImage(ImageRq request, Long userId) {
        return settingsService.getActiveProvider()
                .flatMap(activeProvider -> {
                    ImageGenerationProvider provider = providers.get(activeProvider);
                    if (provider == null) {
                        log.error("Провайдер {} не найден в списке доступных провайдеров", activeProvider);
                        return Mono.error(new IllegalStateException("Провайдер " + activeProvider + " не найден"));
                    }
                    log.debug("Маршрутизация запроса к провайдеру: {}", activeProvider);
                    return provider.generateImage(request, userId);
                });
    }

    /**
     * Получить активного провайдера.
     *
     * @return активный провайдер
     */
    public Mono<GenerationProvider> getActiveProvider() {
        return settingsService.getActiveProvider();
    }

    /**
     * Получить провайдер по имени.
     *
     * @param provider имя провайдера
     * @return провайдер или null если не найден
     */
    public ImageGenerationProvider getProvider(GenerationProvider provider) {
        return providers.get(provider);
    }
}
