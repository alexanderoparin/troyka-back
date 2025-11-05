package ru.oparin.troyka.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.oparin.troyka.model.entity.ArtStyle;

/**
 * DTO для стиля изображения.
 * Используется для передачи информации о стиле на фронтенд.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtStyleDTO {

    private Long id;
    private String name;
    private String prompt;

    public static ArtStyleDTO fromEntity(ArtStyle artStyle) {
        return ArtStyleDTO.builder()
                .id(artStyle.getId())
                .name(artStyle.getName())
                .prompt(artStyle.getPrompt())
                .build();
    }
}

