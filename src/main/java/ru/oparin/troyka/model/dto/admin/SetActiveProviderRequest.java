package ru.oparin.troyka.model.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса на установку активного провайдера для модели.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetActiveProviderRequest {

    /**
     * Тип модели (NANO_BANANA, NANO_BANANA_PRO и т.д.).
     */
    @NotBlank(message = "Тип модели не может быть пустым")
    private String modelType;

    /**
     * Код провайдера для установки (FAL_AI или LAOZHANG_AI).
     */
    @NotBlank(message = "Код провайдера не может быть пустым")
    private String provider;
}
