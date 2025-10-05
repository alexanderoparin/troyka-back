package ru.oparin.troyka.model.dto.contact;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос на отправку сообщения через контактную форму")
public class ContactRequest {

    @NotBlank(message = "Имя обязательно")
    @Size(min = 2, max = 100, message = "Имя должно содержать от 2 до 100 символов")
    @Schema(description = "Имя отправителя", example = "Иван Иванов")
    private String name;

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный формат email")
    @Schema(description = "Email отправителя", example = "ivan@example.com")
    private String email;

    @Schema(description = "Телефон", example = "+7 (999) 123-45-67")
    private String phone;

    @NotBlank(message = "Тема обязательна")
    @Size(min = 5, max = 200, message = "Тема должна содержать от 5 до 200 символов")
    @Schema(description = "Тема сообщения", example = "Вопрос по тарифам")
    private String subject;

    @NotBlank(message = "Сообщение обязательно")
    @Size(min = 10, max = 2000, message = "Сообщение должно содержать от 10 до 2000 символов")
    @Schema(description = "Текст сообщения", example = "Здравствуйте! У меня вопрос по тарифным планам...")
    private String message;
}
