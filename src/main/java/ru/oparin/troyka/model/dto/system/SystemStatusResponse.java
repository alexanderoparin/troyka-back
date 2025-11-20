package ru.oparin.troyka.model.dto.system;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.oparin.troyka.model.enums.SystemStatus;

/**
 * DTO для публичного API статуса системы.
 * Используется для отображения баннеров на фронтенде.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatusResponse {
    
    /**
     * Текущий статус системы.
     */
    private SystemStatus status;
    
    /**
     * Сообщение для пользователей.
     * Может быть null, если статус ACTIVE.
     */
    private String message;
}

