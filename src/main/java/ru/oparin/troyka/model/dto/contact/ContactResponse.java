package ru.oparin.troyka.model.dto.contact;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ответ на отправку сообщения через контактную форму")
public class ContactResponse {

    @Schema(description = "Сообщение о результате", example = "Сообщение успешно отправлено")
    private String message;

    @Schema(description = "Статус отправки", example = "success")
    private String status;

    @Schema(description = "ID сообщения для отслеживания", example = "contact_2024_001")
    private String messageId;
}
