package ru.oparin.troyka.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.troyka.model.dto.fal.FalAIQueueRequestStatusDTO;
import ru.oparin.troyka.model.dto.fal.FalAIRequestDTO;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.Resolution;

import java.util.List;
import java.util.Map;

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
     * Маппинг aspect_ratio -> image_size для Seedream 4.5.
     * Допустимые значения FAL: square_hd, square, portrait_4_3, portrait_16_9,
     * landscape_4_3, landscape_16_9, auto_2K, auto_4K.
     */
    private static final Map<String, String> SEEDREAM_IMAGE_SIZE_BY_ASPECT = Map.ofEntries(
            Map.entry("1:1", "square_hd"),
            Map.entry("4:3", "landscape_4_3"),
            Map.entry("3:4", "portrait_4_3"),
            Map.entry("16:9", "landscape_16_9"),
            Map.entry("9:16", "portrait_16_9"),
            Map.entry("3:2", "landscape_4_3"),
            Map.entry("2:3", "portrait_4_3"),
            Map.entry("21:9", "landscape_16_9"),
            Map.entry("5:4", "landscape_4_3"),
            Map.entry("4:5", "portrait_4_3")
    );
    /** Fallback для неизвестного aspect_ratio (допустимое значение FAL). */
    private static final String SEEDREAM_IMAGE_SIZE_DEFAULT = "auto_2K";

    /**
     * Создать тело запроса для Fal.ai.
     */
    public FalAIRequestDTO createRqBody(ImageRq rq, String prompt, Integer numImages, List<String> inputImageUrls, Resolution resolution) {
        FalAIRequestDTO.FalAIRequestDTOBuilder builder = FalAIRequestDTO.builder()
                .prompt(prompt)
                .numImages(numImages)
                .imageUrls(removingBlob(inputImageUrls))
                .aspectRatio(rq.getAspectRatio());

        GenerationModelType model = rq.getModel();
        if (model == GenerationModelType.SEEDREAM_4_5) {
            String size = rq.getSeedreamImageSize();
            if (size != null && !size.isBlank()) {
                builder.imageSize(size.trim());
            } else {
                String aspect = rq.getAspectRatio() != null ? rq.getAspectRatio() : "1:1";
                builder.imageSize(SEEDREAM_IMAGE_SIZE_BY_ASPECT.getOrDefault(aspect, SEEDREAM_IMAGE_SIZE_DEFAULT));
            }
        } else {
            if (model.supportsResolution() && resolution != null) {
                builder.resolution(resolution.getValue());
            }
        }

        return builder.build();
    }
}
