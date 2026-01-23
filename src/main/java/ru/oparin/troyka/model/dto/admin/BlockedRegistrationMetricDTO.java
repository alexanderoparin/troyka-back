package ru.oparin.troyka.model.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO для отображения метрики блокированной регистрации.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockedRegistrationMetricDTO {

    private Long id;
    private String email;
    private String emailDomain;
    private String username;
    private String ipAddress;
    private String userAgent;
    private String registrationMethod;
    private LocalDateTime createdAt;
}
