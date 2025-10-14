package ru.oparin.troyka.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;
import ru.oparin.troyka.validation.StrongPassword;

@ToString
@Data
public class RegisterRequest {

    @NotBlank
    @Schema(description = "Уникальное имя пользователя", example = "ivan_petrov")
    private String username;

    @NotBlank
    @Email
    @Schema(description = "Email адрес", example = "ivan@example.com")
    private String email;

    @ToString.Exclude
    @NotBlank(message = "Пароль обязателен")
    @StrongPassword
    @Schema(description = "Пароль (минимум 8 символов, включая заглавные и строчные буквы, цифры и специальные символы)", example = "SecurePass123!")
    private String password;
}