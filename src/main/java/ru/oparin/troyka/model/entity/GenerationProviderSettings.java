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
 * Хранит информацию о том, какой провайдер активен в системе.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "generation_provider_settings", schema = "troyka")
public class GenerationProviderSettings {

    /**
     * Уникальный идентификатор настройки.
     * Всегда будет равен 1, так как настройка одна для всей системы.
     */
    @Id
    private Long id;

    /**
     * Активный провайдер генерации изображений.
     * По умолчанию FAL_AI.
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
