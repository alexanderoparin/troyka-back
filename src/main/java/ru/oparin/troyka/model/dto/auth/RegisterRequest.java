package ru.oparin.troyka.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    @Schema(description = "Уникальное имя пользователя", example = "ivan_petrov")
    private String username;

    @NotBlank
    @Email
    @Schema(description = "Email адрес", example = "ivan@example.com")
    private String email;

    @NotBlank
    @Size(min = 6)
    @Schema(description = "Пароль", example = "securePassword123")
    private String password;

    @Schema(description = "Имя", example = "Иван")
    private String firstName;

    @Schema(description = "Фамилия", example = "Петров")
    private String lastName;

    @Override
    public String toString() {
        return "RegisterRequest{" +
                "username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", password='" + "***" + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                '}';
    }
}