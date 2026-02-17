package ru.oparin.troyka.model.dto.laozhang;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO ответа LaoZhang SeeDream API (OpenAI-совместимый формат).
 * data[].b64_json — base64 изображения при response_format=b64_json.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LaoZhangSeedreamResponseDTO {

    @JsonProperty("created")
    private Long created;

    @JsonProperty("data")
    private List<ImageData> data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageData {
        @JsonProperty("url")
        private String url;

        @JsonProperty("b64_json")
        private String b64Json;

        @JsonProperty("revised_prompt")
        private String revisedPrompt;
    }
}
