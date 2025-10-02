package ru.oparin.troyka.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table(value = "password_reset_tokens", schema = "troyka")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetToken {

    @Id
    private Long id;

    private Long userId;

    private String token;

    private LocalDateTime expiresAt;

    @Builder.Default
    private Boolean used = false;

    @CreatedDate
    private LocalDateTime createdAt;
}
