package ru.oparin.troyka.model.dto.fal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для запроса к fal.ai API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FalAIRequestDTO {

    @JsonProperty("prompt")
    private String prompt;

    @JsonProperty("num_images")
    private Integer numImages;

    @JsonProperty("aspect_ratio")
    private String aspectRatio;

    @JsonProperty("image_urls")
    private List<String> imageUrls;

    @JsonProperty("resolution")
    private String resolution;
}

