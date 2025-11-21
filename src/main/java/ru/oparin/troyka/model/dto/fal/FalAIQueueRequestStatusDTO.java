package ru.oparin.troyka.model.dto.fal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.oparin.troyka.model.enums.QueueStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO для статуса запроса генерации для фронтенда.
 * Содержит информацию о запросе и его текущем статусе в очереди.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FalAIQueueRequestStatusDTO {

    /**
     * Внутренний идентификатор записи в ImageGenerationHistory.
     */
    private Long id;

    /**
     * Идентификатор запроса в очереди Fal.ai.
     */
    private String falRequestId;

    /**
     * Статус запроса в очереди.
     * Возможные значения: IN_QUEUE, IN_PROGRESS, COMPLETED, FAILED
     */
    private QueueStatus queueStatus;

    /**
     * Позиция запроса в очереди.
     */
    private Integer queuePosition;

    /**
     * Промпт, использованный для генерации.
     */
    private String prompt;

    /**
     * URL сгенерированных изображений (заполняется при COMPLETED).
     */
    private List<String> imageUrls;

    /**
     * Описание изображения от ИИ (если доступно).
     */
    private String description;

    /**
     * Идентификатор сессии.
     */
    private Long sessionId;

    /**
     * Дата и время создания запроса.
     */
    private LocalDateTime createdAt;

    /**
     * Дата и время последнего обновления статуса.
     */
    private LocalDateTime updatedAt;
}

