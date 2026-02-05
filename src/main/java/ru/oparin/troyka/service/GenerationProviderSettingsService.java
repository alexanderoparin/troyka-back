package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.GenerationProviderSettings;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.repository.GenerationProviderSettingsRepository;

import java.time.LocalDateTime;

/**
 * Сервис для управления настройками провайдера генерации изображений.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationProviderSettingsService {

    private static final Long DEFAULT_SETTINGS_ID = 1L;

    private final GenerationProviderSettingsRepository repository;

    /**
     * Получить активного провайдера.
     * Если настройки не существуют, создает дефолтные (FAL_AI).
     *
     * @return активный провайдер
     */
    public Mono<GenerationProvider> getActiveProvider() {
        return repository.findById(DEFAULT_SETTINGS_ID)
                .switchIfEmpty(createDefaultSettings().onErrorResume(error -> {
                    // Если при создании дефолтных настроек произошла ошибка (например, запись уже создана другим потоком),
                    // пытаемся получить существующую запись
                    log.debug("Ошибка при создании дефолтных настроек (возможно, запись уже существует), пытаемся получить существующую: {}", error.getMessage());
                    return repository.findById(DEFAULT_SETTINGS_ID);
                }))
                .map(GenerationProviderSettings::getActiveProviderEnum);
    }

    /**
     * Установить активного провайдера.
     *
     * @param provider провайдер для установки
     * @return обновленные настройки
     */
    public Mono<GenerationProviderSettings> setActiveProvider(GenerationProvider provider) {
        log.info("Установка активного провайдера: {}", provider);
        return repository.findById(DEFAULT_SETTINGS_ID)
                .switchIfEmpty(createDefaultSettings())
                .flatMap(settings -> {
                    settings.setActiveProviderEnum(provider);
                    settings.setUpdatedAt(LocalDateTime.now());
                    return repository.save(settings);
                })
                .doOnNext(settings -> log.info("Активный провайдер установлен: {}", settings.getActiveProviderEnum()));
    }

    /**
     * Получить настройки провайдера.
     *
     * @return настройки провайдера
     */
    public Mono<GenerationProviderSettings> getSettings() {
        return repository.findById(DEFAULT_SETTINGS_ID)
                .switchIfEmpty(createDefaultSettings());
    }

    /**
     * Создать дефолтные настройки.
     * ВНИМАНИЕ: Этот метод может быть вызван параллельно из разных потоков.
     * Если запись уже существует, операция save может завершиться успешно (upsert),
     * но это не проблема, так как мы затем получим существующую запись.
     *
     * @return дефолтные настройки
     */
    private Mono<GenerationProviderSettings> createDefaultSettings() {
        // Проверяем, существует ли запись перед созданием
        return repository.findById(DEFAULT_SETTINGS_ID)
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        // Запись уже существует, просто возвращаем её
                        log.debug("Настройки провайдера уже существуют, используем существующие");
                        return repository.findById(DEFAULT_SETTINGS_ID);
                    } else {
                        // Записи нет, создаем дефолтные
                        log.info("Создание дефолтных настроек провайдера (FAL_AI)");
                        GenerationProviderSettings defaultSettings = GenerationProviderSettings.builder()
                                .id(DEFAULT_SETTINGS_ID)
                                .activeProvider(GenerationProvider.FAL_AI.getCode())
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        return repository.save(defaultSettings)
                                .doOnSuccess(settings -> log.info("Дефолтные настройки провайдера (FAL_AI) успешно созданы"))
                                .onErrorResume(error -> {
                                    // Если при сохранении произошла ошибка (например, запись создана другим потоком),
                                    // пытаемся получить существующую запись
                                    log.debug("Ошибка при создании дефолтных настроек (возможно, запись уже создана другим потоком), получаем существующую: {}", error.getMessage());
                                    return repository.findById(DEFAULT_SETTINGS_ID);
                                });
                    }
                });
    }
}
