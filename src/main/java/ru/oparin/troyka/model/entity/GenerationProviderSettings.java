package ru.oparin.troyka.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import ru.oparin.troyka.model.enums.GenerationProvider;

import java.time.LocalDateTime;

/**
 * Сущность настроек провайдера генерации изображений.
 * Одна запись на модель: для каждой модели (NANO_BANANA, NANO_BANANA_PRO, …) хранится свой активный провайдер.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "generation_provider_settings", schema = "troyka")
public class GenerationProviderSettings {

    @Id
    private Long id;

    /**
     * Тип модели генерации (NANO_BANANA, NANO_BANANA_PRO и т.д.).
     */
    @Column("model_type")
    private String modelType;

    /**
     * Активный провайдер для этой модели (FAL_AI, LAOZHANG_AI).
     */
    @Column("active_provider")
    @Builder.Default
    private String activeProvider = GenerationProvider.FAL_AI.getCode();

    /**
     * Дата и время создания настройки.
     */
    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    /**
     * Дата и время последнего обновления настройки.
     */
    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    /**
     * Получить активного провайдера как enum.
     *
     * @return активный провайдер
     */
    public GenerationProvider getActiveProviderEnum() {
        return GenerationProvider.fromCode(activeProvider);
    }

    /**
     * Установить активного провайдера из enum.
     *
     * @param provider провайдер
     */
    public void setActiveProviderEnum(GenerationProvider provider) {
        this.activeProvider = provider.getCode();
    }
}
