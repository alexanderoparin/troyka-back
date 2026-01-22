package ru.oparin.troyka.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.dto.fal.ImageRs;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.service.GenerationProviderSettingsService;
import ru.oparin.troyka.service.ProviderFallbackMetricsService;

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
    private final ProviderErrorHandler errorHandler;
    private final ProviderFallbackMetricsService metricsService;

    /**
     * Генерировать изображение через активного провайдера с автоматическим fallback на резервный.
     * <p>
     * Если активный провайдер возвращает ошибку, требующую fallback, автоматически
     * переключается на резервный провайдер. Переключение логируется в метрики.
     *
     * @param request запрос на генерацию изображения
     * @param userId  идентификатор пользователя
     * @return ответ с изображениями
     * @throws IllegalStateException если активный провайдер не найден в списке доступных
     */
    public Mono<ImageRs> generateImage(ImageRq request, Long userId) {
        return settingsService.getActiveProvider()
                .flatMap(activeProvider -> {
                    ImageGenerationProvider activeProviderInstance = getProviderOrError(activeProvider);
                    log.debug("Маршрутизация запроса к активному провайдеру: {}", activeProvider);
                    
                    // Пробуем активного провайдера
                    return activeProviderInstance.generateImage(request, userId)
                            .onErrorResume(error -> {
                                // Проверяем, требует ли ошибка fallback
                                if (errorHandler.requiresFallback(error)) {
                                    return handleFallback(activeProvider, request, userId, error);
                                }
                                // Если fallback не требуется, просто пробрасываем ошибку
                                return Mono.error(error);
                            });
                });
    }

    /**
     * Обработать fallback переключение на резервный провайдер.
     *
     * @param activeProvider активный провайдер, который не смог выполнить запрос
     * @param request        запрос на генерацию
     * @param userId         идентификатор пользователя
     * @param error          ошибка от активного провайдера
     * @return ответ с изображениями от резервного провайдера
     */
    private Mono<ImageRs> handleFallback(
            GenerationProvider activeProvider,
            ImageRq request,
            Long userId,
            Throwable error) {
        
        GenerationProvider fallbackProvider = getFallbackProvider(activeProvider);
        ImageGenerationProvider fallbackProviderInstance = getProviderOrError(fallbackProvider);
        
        log.warn("Активный провайдер {} не смог выполнить запрос для userId={}, " +
                 "переключаемся на резервный провайдер {}: {}",
                activeProvider, userId, fallbackProvider, error.getMessage());
        
        // Извлекаем информацию об ошибке для метрик
        String[] errorInfo = errorHandler.extractErrorInfo(error);
        String errorType = errorInfo[0];
        Integer httpStatus = errorInfo[1] != null ? Integer.parseInt(errorInfo[1]) : null;
        String errorMessage = errorInfo[2];
        
        // Записываем метрику fallback (асинхронно, не блокируем генерацию)
        metricsService.recordFallback(
                activeProvider,
                fallbackProvider,
                errorType,
                httpStatus,
                errorMessage,
                userId
        ).subscribe(
                metric -> log.debug("Метрика fallback записана: id={}", metric.getId()),
                metricsError -> log.error("Ошибка при записи метрики fallback: {}", metricsError.getMessage())
        );
        
        // Пробуем резервного провайдера
        log.info("Попытка генерации через резервный провайдер {} для userId={}", fallbackProvider, userId);
        return fallbackProviderInstance.generateImage(request, userId)
                .doOnSuccess(result -> log.info("Резервный провайдер {} успешно выполнил запрос для userId={}",
                        fallbackProvider, userId))
                .doOnError(fallbackError -> log.error("Резервный провайдер {} также не смог выполнить запрос для userId={}: {}",
                        fallbackProvider, userId, fallbackError.getMessage()));
    }

    /**
     * Получить резервного провайдера для указанного активного.
     * <p>
     * Если активен FAL_AI, резервный - LAOZHANG_AI.
     * Если активен LAOZHANG_AI, резервный - FAL_AI.
     *
     * @param activeProvider активный провайдер
     * @return резервный провайдер
     */
    private GenerationProvider getFallbackProvider(GenerationProvider activeProvider) {
        return activeProvider == GenerationProvider.FAL_AI
                ? GenerationProvider.LAOZHANG_AI
                : GenerationProvider.FAL_AI;
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
