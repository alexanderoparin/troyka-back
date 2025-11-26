package ru.oparin.troyka.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.troyka.model.dto.fal.FalAIQueueRequestStatusDTO;
import ru.oparin.troyka.model.dto.fal.FalAIRequestDTO;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;
import ru.oparin.troyka.model.enums.Resolution;

import java.util.List;

import static ru.oparin.troyka.util.JsonUtils.removingBlob;

@Component
@Slf4j
public class FalAIQueueMapper {

    /**
     * Преобразовать ImageGenerationHistory в FalAIQueueRequestStatusDTO.
     */
    public FalAIQueueRequestStatusDTO toStatusDTO(ImageGenerationHistory history) {
        return FalAIQueueRequestStatusDTO.builder()
                .id(history.getId())
                .falRequestId(history.getFalRequestId())
                .queueStatus(history.getQueueStatus())
                .queuePosition(history.getQueuePosition())
                .prompt(history.getPrompt())
                .imageUrls(history.getImageUrls())
                .sessionId(history.getSessionId())
                .createdAt(history.getCreatedAt())
                .updatedAt(history.getUpdatedAt())
                .build();
    }

    /**
     * Создать тело запроса для Fal.ai.
     */
    public FalAIRequestDTO createRqBody(ImageRq rq, String prompt, Integer numImages, List<String> inputImageUrls, Resolution resolution) {
        FalAIRequestDTO.FalAIRequestDTOBuilder builder = FalAIRequestDTO.builder()
                .prompt(prompt)
                .numImages(numImages)
                .imageUrls(removingBlob(inputImageUrls))
                .aspectRatio(rq.getAspectRatio());
        
        // Добавляем resolution только для моделей, которые его поддерживают и если resolution указан
        if (rq.getModel().supportsResolution() && resolution != null) {
            builder.resolution(resolution.getValue());
        }
        
        return builder.build();
    }
}
