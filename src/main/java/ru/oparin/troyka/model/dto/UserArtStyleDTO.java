package ru.oparin.troyka.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для стиля пользователя.
 * Используется для получения и сохранения выбранного стиля пользователя.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserArtStyleDTO {

    /** Идентификатор выбранного стиля */
    private Long styleId;

    /** Название выбранного стиля */
    private String styleName;
}

