package ru.oparin.solving.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String username;
    private String email;
    private String role;
    private LocalDateTime expiresAt;
}