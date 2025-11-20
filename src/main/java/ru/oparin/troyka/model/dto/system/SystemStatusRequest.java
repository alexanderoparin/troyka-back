package ru.oparin.troyka.model.dto.system;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.oparin.troyka.model.enums.SystemStatus;

/**
 * DTO для запроса обновления статуса системы (админский API).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatusRequest {
    
    /**
     * Новый статус системы.
     */
    @NotNull(message = "Статус обязателен")
    private SystemStatus status;
    
    /**
     * Сообщение для пользователей.
     * Может быть null или пустым, если статус ACTIVE.
     */
    private String message;
}

