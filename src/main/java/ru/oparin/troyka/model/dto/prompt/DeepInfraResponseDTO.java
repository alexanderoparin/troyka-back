package ru.oparin.troyka.model.dto.prompt;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для ответа от DeepInfra API (OpenAI-совместимый формат).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepInfraResponseDTO {

    private String id;
    private String object;
    private Long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        private Integer index;
        private Message message;
        @JsonProperty("finish_reason")
        private String finishReason;
        private ChoiceLogProbs logprobs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
        @JsonProperty("reasoning_content")
        private String reasoningContent;
        private String name;
        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChoiceLogProbs {
        @JsonProperty("token_logprobs")
        private List<Double> tokenLogprobs;
        @JsonProperty("text_offset")
        private List<Integer> textOffset;
        private List<String> tokens;
        @JsonProperty("top_logprobs")
        private List<TopLogProb> topLogprobs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopLogProb {
        private String token;
        private Double logprob;
        private Integer bytes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String type;
        private ToolCallFunction function;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallFunction {
        private String name;
        private String arguments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        @JsonProperty("total_tokens")
        private Integer totalTokens;
        @JsonProperty("estimated_cost")
        private Double estimatedCost;
        @JsonProperty("prompt_tokens_details")
        private PromptTokensDetails promptTokensDetails;
        @JsonProperty("completion_tokens_details")
        private CompletionTokensDetails completionTokensDetails;
        @JsonProperty("extra_properties")
        private ExtraProperties extraProperties;
    }

    @Data
    @Builder
    @NoArgsConstructor
    public static class PromptTokensDetails {
        // Поля могут быть добавлены при необходимости
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompletionTokensDetails {
        @JsonProperty("reasoning_tokens")
        private Integer reasoningTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtraProperties {
        private GoogleProperties google;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoogleProperties {
        @JsonProperty("traffic_type")
        private String trafficType;
    }
}

