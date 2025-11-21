package ru.oparin.troyka.model.dto.fal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.oparin.troyka.model.enums.QueueStatus;

/**
 * DTO для статуса запроса в очереди Fal.ai.
 * Возвращается при проверке статуса запроса через API очереди.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FalAIQueueStatusDTO {

    /**
     * Статус запроса в очереди Fal.ai.
     * Возможные значения: IN_QUEUE, IN_PROGRESS, COMPLETED, FAILED
     */
    @JsonProperty("status")
    @JsonDeserialize(using = QueueStatusDeserializer.class)
    private QueueStatus status;

    /**
     * Позиция запроса в очереди.
     * Присутствует только когда status = IN_QUEUE.
     */
    @JsonProperty("queue_position")
    private Integer queuePosition;

    /**
     * URL для получения результата запроса.
     * Присутствует только когда status = COMPLETED.
     */
    @JsonProperty("response_url")
    private String responseUrl;
}

