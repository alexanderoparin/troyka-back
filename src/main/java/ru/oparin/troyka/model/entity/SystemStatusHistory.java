package ru.oparin.troyka.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import ru.oparin.troyka.model.enums.SystemStatus;

import java.time.LocalDateTime;

/**
 * История изменений статуса системы.
 * Хранит информацию о всех изменениях статуса системы, включая автоматические и ручные.
 */
@Table(value = "system_status_history", schema = "troyka")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatusHistory {

    /**
     * Уникальный идентификатор записи истории.
     * Автоматически генерируется базой данных.
     */
    @Id
    private Long id;

    /**
     * Статус системы (ACTIVE, DEGRADED, MAINTENANCE).
     */
    @Column("status")
    private SystemStatus status;

    /**
     * Текст сообщения для пользователей.
     * Может быть null, если статус ACTIVE.
     */
    private String message;

    /**
     * Идентификатор пользователя, который изменил статус.
     * Может быть null, если изменение было автоматическим (isSystem = true).
     */
    @Column("user_id")
    private Long userId;

    /**
     * Флаг, указывающий, что изменение было автоматическим (системным).
     * true - автоматическое изменение после проверки FAL AI,
     * false - ручное изменение администратором.
     */
    @Column("is_system")
    private Boolean isSystem;

    /**
     * Дата и время изменения статуса.
     * Автоматически устанавливается при создании записи.
     */
    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;
}

