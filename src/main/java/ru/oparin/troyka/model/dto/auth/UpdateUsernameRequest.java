package ru.oparin.troyka.model.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса обновления имени пользователя.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUsernameRequest {

    /**
     * Новое имя пользователя.
     * Должно быть уникальным и не пустым.
     */
    @NotBlank(message = "Имя пользователя не может быть пустым")
    @Size(min = 3, max = 50, message = "Имя пользователя должно содержать от 3 до 50 символов")
    private String username;
}
