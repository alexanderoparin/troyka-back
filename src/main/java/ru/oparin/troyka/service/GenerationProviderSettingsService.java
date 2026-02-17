package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.GenerationProviderSettings;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.repository.GenerationProviderSettingsRepository;

import java.time.LocalDateTime;

/**
 * Сервис для управления настройками провайдера генерации изображений.
 * Активный провайдер хранится отдельно для каждой модели (NANO_BANANA, NANO_BANANA_PRO и т.д.).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationProviderSettingsService {

    private static final GenerationProvider DEFAULT_PROVIDER = GenerationProvider.LAOZHANG_AI;

    private final GenerationProviderSettingsRepository repository;

    /**
     * Получить активного провайдера для указанной модели.
     * Если записи для модели нет, создаётся запись с провайдером по умолчанию (LAOZHANG_AI).
     */
    public Mono<GenerationProvider> getActiveProvider(GenerationModelType modelType) {
        String modelTypeName = modelType.name();
        return repository.findByModelType(modelTypeName)
                .switchIfEmpty(createDefaultForModel(modelTypeName))
                .map(GenerationProviderSettings::getActiveProviderEnum);
    }

    /**
     * Установить активного провайдера для указанной модели.
     */
    public Mono<GenerationProviderSettings> setActiveProvider(GenerationModelType modelType, GenerationProvider provider) {
        log.info("Установка активного провайдера для модели {}: {}", modelType.name(), provider);
        String modelTypeName = modelType.name();
        return repository.findByModelType(modelTypeName)
                .switchIfEmpty(createDefaultForModel(modelTypeName))
                .flatMap(settings -> {
                    settings.setActiveProviderEnum(provider);
                    settings.setUpdatedAt(LocalDateTime.now());
                    return repository.save(settings);
                })
                .doOnNext(settings -> log.info("Активный провайдер для модели {} установлен: {}", modelTypeName, settings.getActiveProviderEnum()));
    }

    /**
     * Получить все настройки (по одной на каждую модель).
     */
    public Flux<GenerationProviderSettings> getAllSettings() {
        return repository.findAllOrderByModelType();
    }

    private Mono<GenerationProviderSettings> createDefaultForModel(String modelTypeName) {
        log.info("Создание настроек по умолчанию для модели {} (провайдер: {})", modelTypeName, DEFAULT_PROVIDER);
        GenerationProviderSettings settings = GenerationProviderSettings.builder()
                .modelType(modelTypeName)
                .activeProvider(DEFAULT_PROVIDER.getCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return repository.save(settings)
                .onErrorResume(e -> {
                    log.debug("Ошибка при создании настроек для модели {} (возможно, запись уже есть): {}", modelTypeName, e.getMessage());
                    return repository.findByModelType(modelTypeName);
                });
    }
}
