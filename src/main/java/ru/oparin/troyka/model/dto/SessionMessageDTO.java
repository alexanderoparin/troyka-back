package ru.oparin.troyka.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO для отображения одного сообщения (генерации) в истории сессии.
 * Представляет одну итерацию диалога: промпт пользователя и результат генерации.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMessageDTO {

    /** Уникальный идентификатор записи генерации */
    private Long id;

    /** Промпт пользователя для этой генерации */
    private String prompt;

    /** Список URL сгенерированных изображений */
    private List<String> imageUrls;

    /** Список URL входных изображений, которые были использованы для генерации (из поля imageUrls запроса) */
    private List<String> inputImageUrls;


    /** Дата и время генерации */
    private Instant createdAt;

    /** Количество сгенерированных изображений в этой итерации */
    private Integer imageCount;

    /** Идентификатор стиля изображения */
    private Long styleId;

    /** Название стиля изображения */
    private String styleName;

    /** Соотношение сторон изображения */
    private String aspectRatio;

    /** Тип модели, использованной для генерации */
    private String modelType;

    /**
     * Разрешение изображения.
     * Устанавливается только для PRO-моделей, поддерживающих параметр resolution (например, NANO_BANANA_PRO).
     * Для обычных моделей (NANO_BANANA) может быть null.
     */
    private String resolution;
}
