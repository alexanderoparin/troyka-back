package ru.oparin.troyka.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для ответа API при переименовании сессии.
 * Содержит подтверждение успешного переименования.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenameSessionResponseDTO {

    /** Уникальный идентификатор переименованной сессии */
    private Long id;

    /** Новое название сессии */
    private String name;

    /** Сообщение о результате операции */
    private String message;
}
