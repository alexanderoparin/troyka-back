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
 * Сущность поинтов пользователя.
 * Хранит информацию о балансе поинтов пользователя, которые используются
 * для оплаты генерации изображений и других операций в системе.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "user_points", schema = "troyka")
public class UserPoints {

    /**
     * Идентификатор пользователя.
     * Используется как первичный ключ, ссылается на таблицу user.
     */
    @Id
    private Long userId;

    /**
     * Количество поинтов пользователя.
     * Используется для оплаты генерации изображений (3 поинта за изображение).
     * Может быть отрицательным при недостатке средств.
     */
    private Integer points;

    /**
     * Дата и время создания записи поинтов.
     * Автоматически устанавливается при создании.
     */
    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * Дата и время последнего обновления записи поинтов.
     * Автоматически обновляется при изменении баланса.
     */
    @LastModifiedDate
    private LocalDateTime updatedAt;
}