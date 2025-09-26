package ru.oparin.troyka.model.dto;

import lombok.*;
import ru.oparin.troyka.model.entity.ImageGenerationHistory;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationHistoryDTO {

    private String imageUrl;
    private String prompt;
    private LocalDateTime createdAt;

    public static ImageGenerationHistoryDTO fromEntity(ImageGenerationHistory history) {
        return ImageGenerationHistoryDTO.builder()
                .imageUrl(history.getImageUrl())
                .prompt(history.getPrompt())
                .createdAt(history.getCreatedAt())
                .build();
    }
}