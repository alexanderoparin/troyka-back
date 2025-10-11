package ru.oparin.troyka.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса переименования сессии.
 * Используется при изменении названия существующей сессии.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenameSessionRequestDTO {

    /** Новое название сессии */
    @NotBlank(message = "Название сессии не может быть пустым")
    @Size(max = 255, message = "Название сессии не должно превышать 255 символов")
    private String name;
}
