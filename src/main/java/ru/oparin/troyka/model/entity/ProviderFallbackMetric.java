package ru.oparin.troyka.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import ru.oparin.troyka.model.enums.GenerationProvider;

import java.time.LocalDateTime;

/**
 * Метрика fallback переключения между провайдерами генерации изображений.
 * Записывается каждый раз, когда активный провайдер не смог выполнить запрос
 * и система автоматически переключилась на резервный провайдер.
 */
@Table(value = "provider_fallback_metrics", schema = "troyka")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderFallbackMetric {

    /**
     * Уникальный идентификатор метрики.
     * Автоматически генерируется базой данных.
     */
    @Id
    private Long id;

    /**
     * Активный провайдер, который не смог выполнить запрос.
     */
    @Column("active_provider")
    private String activeProvider;

    /**
     * Резервный провайдер, на который произошло переключение.
     */
    @Column("fallback_provider")
    private String fallbackProvider;

    /**
     * Тип ошибки, которая вызвала fallback.
     * Например: TIMEOUT, CONNECTION_ERROR, HTTP_ERROR, PAYLOAD_TOO_LARGE и т.д.
     */
    @Column("error_type")
    private String errorType;

    /**
     * HTTP статус код ошибки (если применимо).
     * Может быть null для ошибок подключения или таймаутов.
     */
    @Column("http_status")
    private Integer httpStatus;

    /**
     * Сообщение об ошибке (краткое).
     */
    @Column("error_message")
    private String errorMessage;

    /**
     * Идентификатор пользователя, для которого произошло переключение.
     * Может быть null, если ошибка произошла на системном уровне.
     */
    @Column("user_id")
    private Long userId;

    /**
     * Дата и время переключения на резервный провайдер.
     * Автоматически устанавливается при создании записи.
     */
    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    /**
     * Получить активного провайдера как enum.
     *
     * @return активный провайдер
     */
    public GenerationProvider getActiveProviderEnum() {
        return GenerationProvider.fromCode(activeProvider);
    }

    /**
     * Получить резервного провайдера как enum.
     *
     * @return резервный провайдер
     */
    public GenerationProvider getFallbackProviderEnum() {
        return GenerationProvider.fromCode(fallbackProvider);
    }

    /**
     * Установить активного провайдера из enum.
     *
     * @param provider провайдер
     */
    public void setActiveProviderEnum(GenerationProvider provider) {
        this.activeProvider = provider.getCode();
    }

    /**
     * Установить резервного провайдера из enum.
     *
     * @param provider провайдер
     */
    public void setFallbackProviderEnum(GenerationProvider provider) {
        this.fallbackProvider = provider.getCode();
    }
}
