package ru.oparin.troyka.model.dto;

import lombok.*;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationHistoryDTO {

    private List<String> imageUrls;
    private String prompt;
    private LocalDateTime createdAt;
    private String description;

    public static ImageGenerationHistoryDTO fromEntity(ImageGenerationHistory history) {
        return ImageGenerationHistoryDTO.builder()
                .imageUrls(history.getImageUrls())
                .prompt(history.getPrompt())
                .createdAt(history.getCreatedAt())
                .description(history.getDescription())
                .build();
    }
}