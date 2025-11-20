package ru.oparin.troyka.model.dto.system;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.oparin.troyka.model.enums.SystemStatus;

import java.time.LocalDateTime;

/**
 * DTO для истории изменений статуса системы (админский API).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemStatusHistoryDTO {
    
    /**
     * Идентификатор записи истории.
     */
    private Long id;
    
    /**
     * Статус системы.
     */
    private SystemStatus status;
    
    /**
     * Сообщение для пользователей.
     */
    private String message;
    
    /**
     * Имя пользователя, который изменил статус.
     * Может быть null, если изменение было автоматическим.
     */
    private String username;
    
    /**
     * Флаг автоматического изменения.
     */
    private Boolean isSystem;
    
    /**
     * Дата и время изменения статуса.
     */
    private LocalDateTime createdAt;
}

