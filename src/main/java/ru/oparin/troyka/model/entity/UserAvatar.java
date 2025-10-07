package ru.oparin.troyka.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Сущность аватара пользователя.
 * Хранит информацию об аватаре пользователя, включая URL изображения
 * и метаданные файла.
 */
@Table(value = "user_avatar", schema = "troyka")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAvatar {

    /**
     * Идентификатор пользователя.
     * Используется как первичный ключ, ссылается на таблицу user.
     * Каждый пользователь может иметь только один аватар.
     */
    @Id
    private Long userId;

    /**
     * URL аватара пользователя.
     * Ссылка на файл изображения аватара, хранящийся в файловой системе.
     */
    private String avatarUrl;

    /**
     * Дата и время загрузки аватара.
     * Автоматически устанавливается при создании записи.
     */
    @CreatedDate
    private LocalDateTime createdAt;
}