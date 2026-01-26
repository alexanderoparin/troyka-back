package ru.oparin.troyka.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.config.properties.FalAiProperties;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.dto.fal.ImageRs;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.model.enums.Resolution;
import ru.oparin.troyka.service.FalAIQueueService;

/**
 * Провайдер генерации изображений через FAL AI.
 * <p>
 * Использует очередь FAL AI для генерации изображений вместо прямых запросов.
 * Это позволяет избежать таймаутов и обеспечивает более надежную работу.
 * <p>
 * Особенности:
 * <ul>
 *   <li>Генерация через очередь queue.fal.run</li>
 *   <li>Синхронное ожидание результата для fallback</li>
 *   <li>Поддержка генерации и редактирования изображений</li>
 *   <li>Автоматическое отслеживание статуса генерации</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FalAIProvider implements ImageGenerationProvider {

    private final FalAIQueueService falAIQueueService;
    private final GenerationProperties generationProperties;
    private final FalAiProperties falAiProperties;

    @Override
    public Mono<ImageRs> generateImage(ImageRq request, Long userId) {
        log.debug("FalAIProvider: генерация изображения через очередь для пользователя {}", userId);
        // Используем очередь вместо прямых запросов для избежания таймаутов
        return falAIQueueService.generateImageSync(request, userId);
    }

    @Override
    public GenerationProvider getProviderName() {
        return GenerationProvider.FAL_AI;
    }

    @Override
    public Mono<Boolean> isAvailable() {
        // Проверяем наличие API ключа для базовой проверки доступности
        String apiKey = falAiProperties.getApi().getKey();
        boolean isConfigured = apiKey != null && !apiKey.isEmpty();
        
        if (!isConfigured) {
            log.warn("FAL AI API ключ не настроен");
        }
        
        return Mono.just(isConfigured);
    }

    @Override
    public Integer getPricePerImage(GenerationModelType modelType, Resolution resolution) {
        return generationProperties.getPointsNeeded(modelType, resolution, 1);
    }
}
