package ru.oparin.troyka.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @Schema(description = "Имя", example = "Иван")
    private String firstName;

    @Schema(description = "Фамилия", example = "Петров")
    private String lastName;

    @Pattern(regexp = "^$|^\\+?[0-9\\s\\-\\(\\)]{10,20}$", message = "Неверный формат номера телефона")
    @Schema(description = "Номер телефона", example = "+7 (999) 123-45-67")
    private String phone;
}