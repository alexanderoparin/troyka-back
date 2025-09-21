package ru.oparin.troyka.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

    @Schema(description = "JWT токен для аутентификации", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    @Schema(description = "Имя пользователя", example = "ivan_petrov")
    private String username;

    @Schema(description = "Email адрес", example = "ivan@example.com")
    private String email;

    @Schema(description = "Роль пользователя", example = "USER")
    private String role;

    @Schema(description = "Время истечения действия токена", example = "2024-01-01T12:00:00")
    private LocalDateTime expiresAt;
}