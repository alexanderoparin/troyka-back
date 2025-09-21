package ru.oparin.troyka.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 6)
    private String password;

    private String firstName;

    private String lastName;

    @Override
    public String toString() {
        return "RegisterRequest{" +
                "username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", password='" + "***" + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                '}';
    }
}