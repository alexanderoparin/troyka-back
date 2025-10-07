package ru.oparin.troyka.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Сущность тарифного плана.
 * Определяет различные пакеты поинтов, которые пользователи могут приобрести
 * для оплаты генерации изображений и других операций в системе.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "pricing_plans", schema = "troyka")
public class PricingPlan {

    /**
     * Уникальный идентификатор тарифного плана.
     * Используется для идентификации плана в системе.
     */
    @Id
    private String id;

    /**
     * Название тарифного плана.
     * Отображается пользователю в интерфейсе выбора тарифа.
     */
    private String name;

    /**
     * Описание тарифного плана.
     * Содержит дополнительную информацию о плане для пользователя.
     */
    private String description;

    /**
     * Количество поинтов в тарифном плане.
     * Определяет, сколько поинтов получит пользователь при покупке.
     */
    private Integer credits;

    /**
     * Цена тарифного плана в копейках.
     * Используется для точного представления денежных сумм.
     */
    private Integer priceRub;

    /**
     * Цена за один поинт в копейках.
     * Вычисляется как priceRub / credits для отображения стоимости поинта.
     */
    private Integer unitPriceRubComputed;

    /**
     * Статус активности тарифного плана.
     * true - план доступен для покупки, false - скрыт от пользователей.
     * По умолчанию true.
     */
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Флаг популярного тарифного плана.
     * true - план выделяется как рекомендуемый в интерфейсе.
     * По умолчанию false.
     */
    @Builder.Default
    private Boolean isPopular = false;

    /**
     * Порядок сортировки тарифного плана.
     * Используется для определения последовательности отображения планов.
     * По умолчанию 0.
     */
    @Builder.Default
    private Integer sortOrder = 0;

    /**
     * Дата и время создания тарифного плана.
     * Автоматически устанавливается при создании.
     */
    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * Дата и время последнего обновления тарифного плана.
     * Автоматически обновляется при изменении данных.
     */
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
