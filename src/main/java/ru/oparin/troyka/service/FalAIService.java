package ru.oparin.troyka.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Service
public class FalAIService {

    private final WebClient webClient;

    @Value("${fal.ai.api.key:your_api_key}")
    private String apiKey;

    @Value("${fal.ai.api.url:https://queue.fal.run/fal-ai}")
    private String apiUrl;

    @Value("${fal.ai.model:fal-ai/llava}")
    private String model;

    public FalAIService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Key " + apiKey)
                .build();
    }

    public Mono<String> getTextResponse(String prompt) {
        String requestBody = String.format("""
                {
                    "prompt": "%s",
                    "model": "%s"
                }
                """, escapeJson(prompt), model);

        return webClient.post()
                .uri("/fal-ai/" + model)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .timeout(Duration.ofSeconds(30))
                .map(this::extractTextFromResponse)
                .onErrorResume(e -> Mono.just("Error: " + e.getMessage()));
    }

    public Mono<Map<String, Object>> getRequestStatus(String requestId) {
        return webClient.get()
                .uri("/fal-ai/" + model + "/requests/" + requestId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(10));
    }

    private String extractTextFromResponse(Map<String, Object> response) {
        // Реализуйте парсинг ответа в зависимости от формата Fal.ai
        if (response.containsKey("text")) {
            return response.get("text").toString();
        }
        if (response.containsKey("output") && response.get("output") instanceof Map) {
            Map<String, Object> output = (Map<String, Object>) response.get("output");
            if (output.containsKey("text")) {
                return output.get("text").toString();
            }
        }
        return "No text response found";
    }

    private String escapeJson(String input) {
        return input.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}