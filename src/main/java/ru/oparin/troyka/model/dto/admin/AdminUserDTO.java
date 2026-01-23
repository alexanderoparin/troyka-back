package ru.oparin.troyka.model.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO для отображения пользователей в админ-панели.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDTO {
    
    private Long id;
    private String username;
    private String email;
    private String role;
    private Boolean emailVerified;
    private Long telegramId;
    private String telegramUsername;
    private String telegramFirstName;
    private String telegramPhotoUrl;
    private Integer points;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean hasSuccessfulPayment;
    
    /**
     * Флаг блокировки пользователя.
     * true - пользователь заблокирован, false - активен.
     */
    private Boolean blocked;
}

