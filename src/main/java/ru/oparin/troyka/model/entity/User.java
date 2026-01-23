package ru.oparin.troyka.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;
import ru.oparin.troyka.model.enums.Role;

import java.time.LocalDateTime;

/**
 * Сущность пользователя системы.
 * Представляет основную информацию о пользователе, включая аутентификационные данные,
 * личную информацию и статус подтверждения email.
 */
@Table(value = "user", schema = "troyka")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /**
     * Уникальный идентификатор пользователя.
     * Автоматически генерируется базой данных.
     */
    @Id
    private Long id;

    /**
     * Уникальное имя пользователя для входа в систему.
     * Должно быть уникальным среди всех пользователей.
     */
    private String username;

    /**
     * Email адрес пользователя.
     * Используется для уведомлений и восстановления пароля.
     * Должен быть уникальным среди всех пользователей.
     */
    private String email;

    /**
     * Хэшированный пароль пользователя.
     * Хранится в зашифрованном виде с использованием bcrypt.
     */
    private String password;


    /**
     * Роль пользователя в системе.
     * По умолчанию USER, определяет права доступа.
     */
    @Builder.Default
    private Role role = Role.USER;

    /**
     * Статус подтверждения email адреса.
     * true - email подтвержден, false - требует подтверждения.
     * По умолчанию false.
     */
    @Builder.Default
    private Boolean emailVerified = false;

    /**
     * ID пользователя в Telegram.
     * Уникальный идентификатор для интеграции с Telegram.
     */
    private Long telegramId;

    /**
     * Username пользователя в Telegram (без @).
     * Может быть null, если пользователь не указал username.
     */
    private String telegramUsername;

    /**
     * Имя пользователя в Telegram.
     * Берется из профиля Telegram.
     */
    private String telegramFirstName;

    /**
     * URL фото профиля из Telegram.
     * Ссылка на аватар пользователя в Telegram.
     */
    private String telegramPhotoUrl;

    /**
     * Флаг блокировки пользователя.
     * true - пользователь заблокирован, false - активен.
     * По умолчанию false (активен).
     */
    @Builder.Default
    private Boolean blocked = false;

    /**
     * Дата и время создания записи пользователя.
     * Автоматически устанавливается при создании.
     */
    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * Дата и время последнего обновления записи пользователя.
     * Автоматически обновляется при изменении данных.
     */
    @LastModifiedDate
    private LocalDateTime updatedAt;
}