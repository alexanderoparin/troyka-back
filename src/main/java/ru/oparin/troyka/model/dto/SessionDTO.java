package ru.oparin.troyka.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO для отображения базовой информации о сессии в списке.
 * Содержит основную информацию для превью сессии в боковой панели.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionDTO {

    /** Уникальный идентификатор сессии */
    private Long id;

    /** Название сессии */
    private String name;

    /** Дата и время создания сессии */
    private Instant createdAt;

    /** Дата и время последнего обновления сессии */
    private Instant updatedAt;

    /** URL последнего сгенерированного изображения в сессии (для превью) */
    private String lastImageUrl;

    /** Общее количество сообщений (генераций) в сессии */
    private Integer messageCount;
}
