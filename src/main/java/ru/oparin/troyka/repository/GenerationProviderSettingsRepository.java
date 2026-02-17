package ru.oparin.troyka.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.entity.GenerationProviderSettings;

/**
 * Репозиторий для работы с настройками провайдера генерации изображений (по одной записи на модель).
 */
@Repository
public interface GenerationProviderSettingsRepository extends R2dbcRepository<GenerationProviderSettings, Long> {

    /**
     * Найти настройки по типу модели.
     */
    @Query("SELECT * FROM troyka.generation_provider_settings WHERE model_type = :modelType")
    Mono<GenerationProviderSettings> findByModelType(String modelType);

    /**
     * Получить все настройки (по одной на каждую модель).
     */
    @Query("SELECT * FROM troyka.generation_provider_settings ORDER BY model_type")
    Flux<GenerationProviderSettings> findAllOrderByModelType();
}
