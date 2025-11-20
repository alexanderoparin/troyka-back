package ru.oparin.troyka.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Статусы системы для отображения баннеров оповещений.
 */
@Getter
@RequiredArgsConstructor
public enum SystemStatus {
    /**
     * Оповещения не показываются.
     */
    ACTIVE(null),
    
    /**
     * Показывается желтый баннер.
     */
    DEGRADED("Система работает с ограничениями, возможны задержки"),
    
    /**
     * Показывается красный баннер.
     */
    MAINTENANCE("Серьезные проблемы с инфраструктурой, сервис может быть недоступен");

    private final String defaultMessage;
}

