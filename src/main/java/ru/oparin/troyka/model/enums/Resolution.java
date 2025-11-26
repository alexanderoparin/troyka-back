package ru.oparin.troyka.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Разрешения изображений для генерации.
 */
@Getter
@RequiredArgsConstructor
public enum Resolution {
    /**
     * Разрешение 1K (по умолчанию).
     */
    RESOLUTION_1K("1K"),

    /**
     * Разрешение 2K.
     */
    RESOLUTION_2K("2K"),

    /**
     * Разрешение 4K.
     */
    RESOLUTION_4K("4K");

    private final String value;

    /**
     * Найти разрешение по значению.
     *
     * @param value строковое значение разрешения
     * @return разрешение или RESOLUTION_1K по умолчанию, если не найдено
     */
    public static Resolution fromValue(String value) {
        for (Resolution resolution : values()) {
            if (resolution.value.equalsIgnoreCase(value)) {
                return resolution;
            }
        }
        return null;
    }
}

