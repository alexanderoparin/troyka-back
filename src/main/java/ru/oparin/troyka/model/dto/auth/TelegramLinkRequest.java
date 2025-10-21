package ru.oparin.troyka.model.dto.auth;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса привязки Telegram к существующему аккаунту.
 * Используется когда пользователь уже зарегистрирован на сайте и хочет привязать Telegram.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramLinkRequest {

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

