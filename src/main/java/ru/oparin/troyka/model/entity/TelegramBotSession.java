package ru.oparin.troyka.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Сущность для связи пользователя со специальной сессией Telegram бота.
 * Каждый пользователь имеет одну специальную сессию для всех генераций через бота.
 */
@Table(value = "telegram_bot_session", schema = "troyka")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramBotSession {

    /**
     * ID пользователя (первичный ключ).
     * Связь с таблицей user.
     */
    @Id
    private Long userId;

    /**
     * ID специальной сессии для Telegram чата.
     * Связь с таблицей session.
     */
    private Long sessionId;

    /**
     * ID чата в Telegram.
     * Уникальный идентификатор чата пользователя с ботом.
     */
    private Long chatId;

    /**
     * Дата и время создания записи.
     * Автоматически устанавливается при создании.
     */
    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * Дата и время последнего обновления записи.
     * Автоматически обновляется при изменении данных.
     */
    @LastModifiedDate
    private LocalDateTime updatedAt;
}

