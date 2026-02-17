package ru.oparin.troyka.model.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO: настройки провайдеров для одной модели (список провайдеров с флагом активного).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelProviderSettingsDTO {

    /**
     * Код модели (NANO_BANANA, NANO_BANANA_PRO и т.д.).
     */
    private String modelType;

    /**
     * Отображаемое имя модели.
     */
    private String modelDisplayName;

    /**
     * Список провайдеров с флагами available и active.
     */
    private List<GenerationProviderDTO> providers;
}
