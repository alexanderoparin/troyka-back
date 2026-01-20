package ru.oparin.troyka.service.provider;

import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.dto.fal.ImageRs;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.model.enums.Resolution;

/**
 * Интерфейс для провайдеров генерации изображений.
 * Все провайдеры должны реализовывать этот интерфейс для единообразной работы.
 */
public interface ImageGenerationProvider {

    /**
     * Генерировать изображение(я) на основе запроса.
     *
     * @param request запрос на генерацию изображения
     * @param userId  идентификатор пользователя
     * @return ответ с сгенерированными изображениями
     */
    Mono<ImageRs> generateImage(ImageRq request, Long userId);

    /**
     * Получить имя провайдера.
     *
     * @return имя провайдера
     */
    GenerationProvider getProviderName();

    /**
     * Проверить доступность провайдера.
     *
     * @return true если провайдер доступен, false в противном случае
     */
    Mono<Boolean> isAvailable();

    /**
     * Получить цену за одно изображение для указанной модели и разрешения.
     *
     * @param modelType  тип модели
     * @param resolution разрешение (может быть null)
     * @return количество поинтов за одно изображение
     */
    Integer getPricePerImage(GenerationModelType modelType, Resolution resolution);
}
