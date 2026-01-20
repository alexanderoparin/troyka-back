package ru.oparin.troyka.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.GenerationProviderSettings;

/**
 * Репозиторий для работы с настройками провайдера генерации изображений.
 */
@Repository
public interface GenerationProviderSettingsRepository extends R2dbcRepository<GenerationProviderSettings, Long> {

    /**
     * Найти настройки по ID (всегда будет 1).
     *
     * @param id идентификатор (обычно 1)
     * @return настройки провайдера
     */
    @Override
    Mono<GenerationProviderSettings> findById(Long id);

    /**
     * Получить единственную настройку (по умолчанию с id=1).
     * Если настройки нет, создает дефолтную.
     *
     * @return настройки провайдера
     */
    @Query("SELECT * FROM troyka.generation_provider_settings WHERE id = 1")
    Mono<GenerationProviderSettings> findDefaultSettings();

    /**
     * Сохранить или обновить настройки.
     * Если настройки с id=1 не существует, создает новую.
     *
     * @param settings настройки для сохранения
     * @return сохраненные настройки
     */
    @Override
    Mono<GenerationProviderSettings> save(GenerationProviderSettings settings);
}
