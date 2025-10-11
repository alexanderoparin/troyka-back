package ru.oparin.troyka.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Entity для таблицы sessions.
 * Представляет сессию генерации изображений - диалог пользователя с AI.
 * Каждая сессия содержит историю генераций и может быть переименована пользователем.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sessions")
public class Session {

    /** Уникальный идентификатор сессии */
    @Id
    private Long id;

    /** Идентификатор пользователя-владельца сессии */
    @Column("user_id")
    private Long userId;

    /** Название сессии (по умолчанию "Сессия {id}", можно переименовать) */
    @Column("name")
    private String name;

    /** Дата и время создания сессии */
    @Column("created_at")
    private Instant createdAt;

    /** Дата и время последнего обновления сессии */
    @Column("updated_at")
    private Instant updatedAt;

    /** Флаг активности сессии (true - активна, false - удалена) */
    @Column("is_active")
    private Boolean isActive;
}

