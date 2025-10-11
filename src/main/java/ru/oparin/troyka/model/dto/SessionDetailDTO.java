package ru.oparin.troyka.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO для отображения детальной информации о сессии с полной историей.
 * Содержит всю информацию о сессии и список сообщений (генераций) в хронологическом порядке.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionDetailDTO {

    /** Уникальный идентификатор сессии */
    private Long id;

    /** Название сессии */
    private String name;

    /** Дата и время создания сессии */
    private Instant createdAt;

    /** Дата и время последнего обновления сессии */
    private Instant updatedAt;

    /** Список сообщений (генераций) в сессии, отсортированных по номеру итерации */
    private List<SessionMessageDTO> history;

    /** Общее количество сообщений в сессии */
    private Integer totalMessages;

    /** Индикатор наличия дополнительных сообщений для загрузки */
    private Boolean hasMore;
}
