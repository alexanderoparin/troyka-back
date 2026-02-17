package ru.oparin.troyka.model.dto.laozhang;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO запроса к LaoZhang SeeDream API (OpenAI-совместимый формат).
 * Документация: https://docs.laozhang.ai/api-capabilities/seedream-image
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaoZhangSeedreamRequestDTO {

    @JsonProperty("model")
    private String model;

    @JsonProperty("prompt")
    private String prompt;

    /**
     * Размер изображения: "2K", "4K" или "WxH" (например "4096x3072").
     */
    @JsonProperty("size")
    private String size;

    /**
     * URL или base64 входного изображения (для image-to-image). Один URL или массив.
     */
    @JsonProperty("image")
    private Object image;

    @JsonProperty("response_format")
    @Builder.Default
    private String responseFormat = "b64_json";

    @JsonProperty("watermark")
    @Builder.Default
    private Boolean watermark = false;

    /**
     * Количество изображений (если API поддерживает).
     */
    @JsonProperty("n")
    private Integer n;
}
