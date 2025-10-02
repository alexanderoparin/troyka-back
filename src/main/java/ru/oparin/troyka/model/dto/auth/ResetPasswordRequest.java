package ru.oparin.troyka.model.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.oparin.troyka.validation.StrongPassword;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {
    
    @NotBlank(message = "Токен обязателен")
    private String token;
    
    @NotBlank(message = "Пароль обязателен")
    @StrongPassword
    private String newPassword;
}

