package ru.oparin.troyka.model.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.oparin.troyka.model.enums.GenerationProvider;

/**
 * DTO для информации о провайдере генерации изображений.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationProviderDTO {

    /**
     * Код провайдера (FAL_AI, LAOZHANG_AI).
     */
    private String code;

    /**
     * Отображаемое имя провайдера.
     */
    private String displayName;

    /**
     * Доступен ли провайдер.
     */
    private Boolean available;

    /**
     * Является ли провайдер активным.
     */
    private Boolean active;

    /**
     * Создать DTO из enum провайдера.
     *
     * @param provider провайдер
     * @param available доступен ли провайдер
     * @param active является ли активным
     * @return DTO провайдера
     */
    public static GenerationProviderDTO fromProvider(GenerationProvider provider, Boolean available, Boolean active) {
        return GenerationProviderDTO.builder()
                .code(provider.getCode())
                .displayName(provider.getDisplayName())
                .available(available)
                .active(active)
                .build();
    }
}
