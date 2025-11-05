package ru.oparin.troyka.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Entity для таблицы user_styles.
 * Хранит выбранный стиль генерации для каждого пользователя.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_styles")
public class UserStyle {

    /** ID пользователя - используется как первичный ключ */
    @Id
    private Long userId;

    /** Идентификатор выбранного стиля (ссылка на art_styles.id). По умолчанию 2 (Реалистичный) */
    @Column("style_id")
    private Long styleId;

    /** Дата и время создания записи */
    private LocalDateTime createdAt;

    /** Дата и время последнего обновления записи */
    private LocalDateTime updatedAt;
}

