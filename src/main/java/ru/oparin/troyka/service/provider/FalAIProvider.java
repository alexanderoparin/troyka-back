package ru.oparin.troyka.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.dto.fal.ImageRs;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.model.enums.Resolution;
import ru.oparin.troyka.service.FalAIService;

/**
 * Провайдер генерации изображений через FAL AI.
 * Обертка над существующим FalAIService для единообразной работы с провайдерами.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FalAIProvider implements ImageGenerationProvider {

    private final FalAIService falAIService;
    private final GenerationProperties generationProperties;

    @Override
    public Mono<ImageRs> generateImage(ImageRq request, Long userId) {
        log.debug("FalAIProvider: генерация изображения для пользователя {}", userId);
        // FalAIService уже сохраняет историю, но нужно передать provider
        // Пока что оставляем как есть, так как FalAIService будет обновлен отдельно
        return falAIService.getImageResponse(request, userId);
    }

    @Override
    public GenerationProvider getProviderName() {
        return GenerationProvider.FAL_AI;
    }

    @Override
    public Mono<Boolean> isAvailable() {
        // FAL AI всегда доступен (можно добавить health check если нужно)
        return Mono.just(true);
    }

    @Override
    public Integer getPricePerImage(GenerationModelType modelType, Resolution resolution) {
        return generationProperties.getPointsNeeded(modelType, resolution, 1);
    }
}
