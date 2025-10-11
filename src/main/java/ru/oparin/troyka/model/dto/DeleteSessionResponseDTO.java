package ru.oparin.troyka.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для ответа API при удалении сессии.
 * Содержит подтверждение успешного удаления.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteSessionResponseDTO {

    /** Уникальный идентификатор удаленной сессии */
    private Long id;

    /** Сообщение о результате операции */
    private String message;

    /** Количество удаленных записей истории генераций */
    private Integer deletedHistoryCount;
}
