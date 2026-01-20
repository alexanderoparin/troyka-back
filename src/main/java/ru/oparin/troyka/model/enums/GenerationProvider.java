package ru.oparin.troyka.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Провайдеры генерации изображений.
 */
@Getter
@RequiredArgsConstructor
public enum GenerationProvider {
    /**
     * FAL AI провайдер (текущий).
     */
    FAL_AI("FAL_AI", "FAL AI"),
    
    /**
     * LaoZhang AI провайдер (новый).
     */
    LAOZHANG_AI("LAOZHANG_AI", "LaoZhang AI");

    private final String code;
    private final String displayName;

    /**
     * Найти провайдер по коду.
     *
     * @param code код провайдера
     * @return провайдер или FAL_AI по умолчанию, если не найден
     */
    public static GenerationProvider fromCode(String code) {
        if (code == null || code.isBlank()) {
            return FAL_AI;
        }
        for (GenerationProvider provider : values()) {
            if (provider.code.equalsIgnoreCase(code)) {
                return provider;
            }
        }
        return FAL_AI;
    }
}
