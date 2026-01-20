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
                .switchIfEmpty(createDefaultSettings())
                .map(GenerationProviderSettings::getActiveProviderEnum)
                .doOnNext(provider -> log.debug("Активный провайдер: {}", provider));
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
     *
     * @return дефолтные настройки
     */
    private Mono<GenerationProviderSettings> createDefaultSettings() {
        log.info("Создание дефолтных настроек провайдера (FAL_AI)");
        GenerationProviderSettings defaultSettings = GenerationProviderSettings.builder()
                .id(DEFAULT_SETTINGS_ID)
                .activeProvider(GenerationProvider.FAL_AI.getCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return repository.save(defaultSettings);
    }
}
