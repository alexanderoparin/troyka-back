package ru.oparin.troyka.model.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса обновления email адреса.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEmailRequest {

    /**
     * Новый email адрес.
     * Должен быть валидным email и не пустым.
     */
    @NotBlank(message = "Email не может быть пустым")
    @Email(message = "Некорректный формат email")
    private String email;
}
