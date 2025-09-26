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
    private String firstName;
    private String lastName;
    private String role;

    public static UserInfoDTO fromUser(User user) {
        return UserInfoDTO.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .build();
    }
}