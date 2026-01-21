package ru.oparin.troyka.service.provider;

import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.dto.fal.ImageRs;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.model.enums.Resolution;

/**
 * Интерфейс для провайдеров генерации изображений.
 * <p>
 * Все провайдеры должны реализовывать этот интерфейс для единообразной работы.
 * Провайдеры могут использовать разные подходы к генерации:
 * <ul>
 *   <li>Синхронная генерация (например, LaoZhang AI)</li>
 *   <li>Асинхронная генерация через очередь (например, FAL AI)</li>
 * </ul>
 * <p>
 * Каждый провайдер должен:
 * <ul>
 *   <li>Валидировать запрос и проверять наличие достаточного количества поинтов</li>
 *   <li>Списать поинты перед генерацией</li>
 *   <li>Вернуть поинты при ошибках</li>
 *   <li>Сохранить историю генерации</li>
 * </ul>
 */
public interface ImageGenerationProvider {

    /**
     * Генерировать изображение(я) на основе запроса.
     * <p>
     * Метод должен:
     * <ul>
     *   <li>Проверить наличие достаточного количества поинтов</li>
     *   <li>Списать поинты перед генерацией</li>
     *   <li>Выполнить генерацию через API провайдера</li>
     *   <li>Сохранить изображения и историю генерации</li>
     *   <li>Вернуть поинты при ошибках</li>
     * </ul>
     *
     * @param request запрос на генерацию изображения
     * @param userId  идентификатор пользователя
     * @return ответ с сгенерированными изображениями и обновленным балансом
     */
    Mono<ImageRs> generateImage(ImageRq request, Long userId);

    /**
     * Получить имя провайдера.
     *
     * @return имя провайдера из enum {@link GenerationProvider}
     */
    GenerationProvider getProviderName();

    /**
     * Проверить доступность провайдера.
     * <p>
     * Базовая проверка доступности (например, наличие API ключа).
     * Для более детальной проверки можно использовать health check сервисы.
     *
     * @return true если провайдер доступен, false в противном случае
     */
    Mono<Boolean> isAvailable();

    /**
     * Получить цену за одно изображение для указанной модели и разрешения.
     * <p>
     * Цена может зависеть от:
     * <ul>
     *   <li>Типа модели (nano-banana, nano-banana-pro)</li>
     *   <li>Разрешения (1K, 2K, 4K для PRO модели)</li>
     * </ul>
     *
     * @param modelType  тип модели
     * @param resolution разрешение (может быть null для базовой модели)
     * @return количество поинтов за одно изображение
     */
    Integer getPricePerImage(GenerationModelType modelType, Resolution resolution);
}
