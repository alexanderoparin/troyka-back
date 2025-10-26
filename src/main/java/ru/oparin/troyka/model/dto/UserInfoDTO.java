package ru.oparin.troyka.model.dto;

import lombok.*;
import ru.oparin.troyka.model.entity.User;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoDTO {

    private String username;
    private String email;
    private String role;
    private String createdAt;
    private Boolean emailVerified;
    private Long telegramId;
    private String telegramUsername;
    private String telegramFirstName;
    private String telegramPhotoUrl;

    public static UserInfoDTO fromUser(User user) {
        return UserInfoDTO.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt().toString())
                .emailVerified(user.getEmailVerified())
                .telegramId(user.getTelegramId())
                .telegramUsername(user.getTelegramUsername())
                .telegramFirstName(user.getTelegramFirstName())
                .telegramPhotoUrl(user.getTelegramPhotoUrl())
                .build();
    }
}