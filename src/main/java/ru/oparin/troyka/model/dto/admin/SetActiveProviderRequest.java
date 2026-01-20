package ru.oparin.troyka.model.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса на установку активного провайдера.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetActiveProviderRequest {

    /**
     * Код провайдера для установки (FAL_AI или LAOZHANG_AI).
     */
    @NotBlank(message = "Код провайдера не может быть пустым")
    private String provider;
}
