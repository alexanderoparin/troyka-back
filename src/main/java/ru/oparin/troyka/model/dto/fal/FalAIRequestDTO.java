package ru.oparin.troyka.model.dto.fal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для запроса к fal.ai API.
 * Поля null не сериализуются в JSON.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    /** Для Seedream 4.5: image_size — строка (enum FAL) или объект { width, height } для пресетов 4k_landscape_4_3 / 4k_portrait_4_3. */
    @JsonProperty("image_size")
    private Object imageSize;
}

