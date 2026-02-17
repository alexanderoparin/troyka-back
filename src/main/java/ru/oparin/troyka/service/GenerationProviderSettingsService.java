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
     * Только из таблицы; если записи нет — возвращается провайдер по умолчанию (без записи в БД).
     */
    public Mono<GenerationProvider> getActiveProvider(GenerationModelType modelType) {
        String modelTypeName = modelType.name();
        return repository.findByModelType(modelTypeName)
                .map(GenerationProviderSettings::getActiveProviderEnum)
                .defaultIfEmpty(DEFAULT_PROVIDER);
    }

    /**
     * Установить активного провайдера для указанной модели.
     * Только обновление существующей записи в таблице; если записи нет — ошибка.
     */
    public Mono<GenerationProviderSettings> setActiveProvider(GenerationModelType modelType, GenerationProvider provider) {
        log.info("Установка активного провайдера для модели {}: {}", modelType.name(), provider);
        String modelTypeName = modelType.name();
        return repository.findByModelType(modelTypeName)
                .switchIfEmpty(Mono.error(new IllegalStateException("Нет записи в generation_provider_settings для модели " + modelTypeName + ". Добавьте строку в таблицу.")))
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
}
