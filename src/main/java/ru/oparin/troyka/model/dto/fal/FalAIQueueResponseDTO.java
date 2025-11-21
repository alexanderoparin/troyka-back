package ru.oparin.troyka.model.dto.fal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для ответа Fal.ai при отправке запроса в очередь.
 * Содержит идентификаторы запроса для последующего отслеживания статуса.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FalAIQueueResponseDTO {

    /**
     * Идентификатор запроса в очереди Fal.ai.
     * Используется для проверки статуса запроса.
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * Идентификатор запроса на шлюзе.
     * Обычно совпадает с request_id, но может отличаться при повторных попытках.
     */
    @JsonProperty("gateway_request_id")
    private String gatewayRequestId;
}

