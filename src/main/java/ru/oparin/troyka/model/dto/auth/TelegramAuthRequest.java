package ru.oparin.troyka.model.dto.auth;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса аутентификации через Telegram Login Widget.
 * Используется как для входа, так и для привязки Telegram к существующему аккаунту.
 * Содержит данные, полученные от Telegram после авторизации пользователя.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramAuthRequest {

    /**
     * ID пользователя в Telegram.
     * Уникальный идентификатор пользователя.
     */
    @NotNull(message = "Telegram ID не может быть пустым")
    private Long id;

    /**
     * Имя пользователя в Telegram.
     * Может быть null, если пользователь не указал имя.
     */
    private String first_name;

    /**
     * Фамилия пользователя в Telegram.
     * Может быть null, если пользователь не указал фамилию.
     */
    private String last_name;

    /**
     * Username пользователя в Telegram (без @).
     * Может быть null, если пользователь не указал username.
     */
    private String username;

    /**
     * URL фото профиля пользователя в Telegram.
     * Может быть null, если у пользователя нет фото.
     */
    private String photo_url;

    /**
     * Email пользователя в Telegram (если указан).
     * Может быть null, так как Telegram не всегда предоставляет email.
     */
    private String email;

    /**
     * Дата авторизации в формате Unix timestamp.
     * Используется для проверки актуальности данных.
     */
    @NotNull(message = "Дата авторизации не может быть пустой")
    private Long auth_date;

    /**
     * Хэш для проверки подлинности данных.
     * Генерируется Telegram на основе bot token и данных пользователя.
     */
    @NotNull(message = "Хэш не может быть пустым")
    private String hash;
}

