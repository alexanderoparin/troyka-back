package ru.oparin.troyka.model.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.oparin.troyka.model.entity.Payment;
import ru.oparin.troyka.model.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO для отображения платежей в админ-панели.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPaymentDTO {
    
    private Long id;
    private Long userId;
    private String username;
    private String email;
    private Long telegramId;
    private String telegramUsername;
    private String telegramFirstName;
    private String telegramPhotoUrl;
    private BigDecimal amount;
    private String description;
    private String status;
    private Integer creditsAmount;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isTest;
    
    public static AdminPaymentDTO fromPayment(Payment payment, User user) {
        return AdminPaymentDTO.builder()
                .id(payment.getId())
                .userId(payment.getUserId())
                .username(user.getUsername())
                .email(user.getEmail() != null ? user.getEmail() : "")
                .telegramId(user.getTelegramId())
                .telegramUsername(user.getTelegramUsername())
                .telegramFirstName(user.getTelegramFirstName())
                .telegramPhotoUrl(user.getTelegramPhotoUrl())
                .amount(payment.getAmount())
                .description(payment.getDescription())
                .status(payment.getStatus().name())
                .creditsAmount(payment.getCreditsAmount())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .isTest(payment.getIsTest())
                .build();
    }
}

