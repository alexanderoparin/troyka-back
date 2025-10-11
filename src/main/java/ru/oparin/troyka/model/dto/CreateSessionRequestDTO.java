package ru.oparin.troyka.model.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса создания новой сессии.
 * Используется при создании сессии через API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequestDTO {

    /** Название новой сессии. Если не указано, будет использовано "Сессия {id}" */
    @Size(max = 255, message = "Название сессии не должно превышать 255 символов")
    private String name;
}
