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
 * Сущность токена подтверждения email.
 * Используется для хранения временных токенов, отправляемых пользователям
 * для подтверждения их email адресов при регистрации.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("email_verification_token")
public class EmailVerificationToken {

    /**
     * Уникальный идентификатор токена.
     * Автоматически генерируется базой данных.
     */
    @Id
    private Long id;

    /**
     * Идентификатор пользователя, для которого создан токен.
     * Ссылается на таблицу user.
     */
    @Column("user_id")
    private Long userId;

    /**
     * Уникальный токен для подтверждения email.
     * Генерируется случайным образом и используется в ссылке подтверждения.
     */
    @Column("token")
    private String token;

    /**
     * Дата и время истечения токена.
     * После истечения токен становится недействительным.
     * Обычно устанавливается на 24 часа с момента создания.
     */
    @Column("expires_at")
    private LocalDateTime expiresAt;

    /**
     * Дата и время создания токена.
     * Используется для отслеживания времени жизни токена.
     */
    @Column("created_at")
    private LocalDateTime createdAt;
}
