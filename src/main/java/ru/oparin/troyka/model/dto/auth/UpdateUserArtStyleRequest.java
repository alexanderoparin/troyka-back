package ru.oparin.troyka.model.dto.auth;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Запрос на обновление стиля пользователя.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserArtStyleRequest {

    /**
     * Идентификатор стиля.
     * По умолчанию 1 (Без стиля).
     */
    @NotNull(message = "Идентификатор стиля обязателен")
    private Long styleId;
}

