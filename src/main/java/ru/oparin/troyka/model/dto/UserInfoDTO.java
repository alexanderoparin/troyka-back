package ru.oparin.troyka.model.dto;

import lombok.*;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.util.PhoneUtil;

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
    private String phone;
    private String role;

    public static UserInfoDTO fromUser(User user) {
        return UserInfoDTO.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(PhoneUtil.formatPhone(user.getPhone()))
                .role(user.getRole().name())
                .build();
    }
}