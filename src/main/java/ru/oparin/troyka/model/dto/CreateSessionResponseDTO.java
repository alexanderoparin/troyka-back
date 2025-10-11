package ru.oparin.troyka.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO для ответа API при создании новой сессии.
 * Содержит информацию о созданной сессии.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionResponseDTO {

    /** Уникальный идентификатор созданной сессии */
    private Long id;

    /** Название созданной сессии */
    private String name;

    /** Дата и время создания сессии */
    private Instant createdAt;

    /** Сообщение о результате операции */
    private String message;
}
