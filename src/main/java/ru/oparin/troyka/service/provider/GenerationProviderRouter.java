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
 * <p>
 * Маршрутизирует запросы к нужному провайдеру на основе настроек системы.
 * Обеспечивает единую точку входа для всех провайдеров генерации изображений.
 * <p>
 * Особенности:
 * <ul>
 *   <li>Автоматический выбор активного провайдера из настроек</li>
 *   <li>Валидация наличия провайдера в списке доступных</li>
 *   <li>Логирование всех маршрутизаций</li>
 * </ul>
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
     * @param request запрос на генерацию изображения
     * @param userId  идентификатор пользователя
     * @return ответ с изображениями
     * @throws IllegalStateException если активный провайдер не найден в списке доступных
     */
    public Mono<ImageRs> generateImage(ImageRq request, Long userId) {
        return settingsService.getActiveProvider()
                .flatMap(activeProvider -> {
                    ImageGenerationProvider provider = getProviderOrError(activeProvider);
                    log.debug("Маршрутизация запроса к провайдеру: {}", activeProvider);
                    return provider.generateImage(request, userId);
                });
    }

    /**
     * Получить активного провайдера из настроек.
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

    /**
     * Получить провайдер или выбросить исключение, если он не найден.
     *
     * @param provider имя провайдера
     * @return провайдер
     * @throws IllegalStateException если провайдер не найден
     */
    private ImageGenerationProvider getProviderOrError(GenerationProvider provider) {
        ImageGenerationProvider foundProvider = providers.get(provider);
        if (foundProvider == null) {
            String message = String.format(
                    ProviderConstants.ErrorMessages.PROVIDER_NOT_FOUND,
                    provider
            );
            log.error(message);
            throw new IllegalStateException(message);
        }
        return foundProvider;
    }
}
