package ru.oparin.troyka.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table(value = "image_generation_history", schema = "troyka")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationHistory {

    @Id
    private Long id;

    private Long userId;

    private String imageUrl;

    private String prompt;

    @CreatedDate
    private LocalDateTime createdAt;
}