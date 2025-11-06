package ru.oparin.troyka.model.dto.prompt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для ответа с улучшенным промптом.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ответ с улучшенным промптом")
public class EnhancePromptResponse {

    /** Улучшенный промпт */
    @Schema(description = "Улучшенный промпт", example = "детальный портрет кота в космическом костюме, фотографическое качество, высокое разрешение")
    private String enhancedPrompt;
}

