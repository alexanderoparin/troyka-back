package ru.oparin.troyka.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы моделей генерации изображений.
 */
@Getter
@RequiredArgsConstructor
public enum GenerationModelType {
    /**
     * Стандартная модель nano-banana (по умолчанию).
     */
    NANO_BANANA("nano-banana", "nano-banana", "nano-banana/edit"),

    /**
     * Продвинутая модель nano-banana-pro с поддержкой разных разрешений.
     */
    NANO_BANANA_PRO("nano-banana-pro", "nano-banana-pro", "nano-banana-pro/edit");

    private final String name;
    private final String createEndpoint;
    private final String editEndpoint;

    /**
     * Найти модель по названию.
     *
     * @param name название модели
     * @return тип модели или NANO_BANANA по умолчанию, если не найдена
     */
    public static GenerationModelType fromName(String name) {
        if (name == null || name.isBlank()) {
            return NANO_BANANA;
        }
        for (GenerationModelType type : values()) {
            if (type.name.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return NANO_BANANA;
    }

    /**
     * Получить endpoint для операции (создание или редактирование).
     *
     * @param isNewImage true для создания нового изображения, false для редактирования
     * @return endpoint модели
     */
    public String getEndpoint(boolean isNewImage) {
        return isNewImage ? createEndpoint : editEndpoint;
    }

    /**
     * Проверить, поддерживает ли модель разные разрешения.
     *
     * @return true если модель поддерживает resolution параметр
     */
    public boolean supportsResolution() {
        return this == NANO_BANANA_PRO;
    }
}

