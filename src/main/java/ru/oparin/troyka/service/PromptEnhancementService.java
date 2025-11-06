package ru.oparin.troyka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.config.properties.DeepInfraProperties;
import ru.oparin.troyka.exception.PromptEnhancementException;
import ru.oparin.troyka.model.dto.prompt.DeepInfraResponseDTO;
import ru.oparin.troyka.model.entity.ArtStyle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис для улучшения промптов через DeepInfra API (Gemini 2.5 Flash).
 */
@Slf4j
@Service
public class PromptEnhancementService {

    private static final String ENDPOINT = "/openai/chat/completions";

    private final WebClient webClient;
    private final DeepInfraProperties properties;

    public PromptEnhancementService(WebClient.Builder webClientBuilder,
                                    DeepInfraProperties deepInfraProperties) {
        this.properties = deepInfraProperties;
        this.webClient = webClientBuilder
                .baseUrl(deepInfraProperties.getApi().getUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + deepInfraProperties.getApi().getKey())
                .build();
    }

    /**
     * Улучшить промпт пользователя с учетом стиля и опциональных изображений.
     *
     * @param userPrompt исходный промпт пользователя
     * @param imageUrls опциональный список URL изображений для анализа
     * @param userStyle стиль пользователя для контекста улучшения
     * @return улучшенный промпт
     */
    public Mono<String> enhancePrompt(String userPrompt, List<String> imageUrls, ArtStyle userStyle) {
        String systemPrompt = buildSystemPrompt(userStyle);
        Map<String, Object> requestBody = buildRequestBody(systemPrompt, userPrompt, imageUrls);
        log.debug("Отправляем запрос на улучшение промпта в model = {}: {}", properties.getModel(), requestBody);

        return webClient.post()
                .uri(ENDPOINT)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<DeepInfraResponseDTO>() {})
                .timeout(Duration.ofMillis(properties.getTimeout()))
                .doOnNext(response -> log.info("Полный ответ от DeepInfra API: {}", response))
                .map(this::extractEnhancedPrompt)
                .onErrorMap(this::mapToPromptEnhancementException);
    }

    /**
     * Построить системный промпт с учетом стиля пользователя (Вариант 2).
     */
    private String buildSystemPrompt(ArtStyle userStyle) {
        String styleName = userStyle.getName() != null ? userStyle.getName() : "общем";
        String stylePrompt = userStyle.getPrompt() != null && !userStyle.getPrompt().trim().isEmpty()
                ? userStyle.getPrompt()
                : "";

        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("Ты — эксперт по созданию промптов для генерации изображений в стиле ")
                .append(styleName)
                .append(".\n")
                .append("Проанализируй промпт пользователя и улучши его, добавив:\n")
                .append("- Детальные визуальные описания в стиле ")
                .append(styleName)
                .append("\n");

        if (!stylePrompt.isEmpty()) {
            systemPrompt.append("- ")
                    .append(stylePrompt)
                    .append("\n");
        }

        systemPrompt.append("- Технические детали композиции и освещения\n\n")
                .append("Сохрани основную идею пользователя. Верни только улучшенный промпт, без дополнительных объяснений.\n")
                .append("ВАЖНО:\n")
                .append("- Отвечай только на русском языке. Улучшенный промпт должен быть полностью на русском языке.\n")
                .append("- Будь кратким и лаконичным. Не добавляй лишних деталей.\n")
                .append("- Фокусируйся на ключевых визуальных элементах.");

        return systemPrompt.toString();
    }

    /**
     * Построить тело запроса в формате OpenAI.
     */
    private Map<String, Object> buildRequestBody(String systemPrompt, String userPrompt, List<String> imageUrls) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", properties.getModel());
        requestBody.put("max_tokens", properties.getMaxTokens());
        requestBody.put("temperature", properties.getTemperature());

        List<Map<String, Object>> messages = new ArrayList<>();

        // Системное сообщение
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        messages.add(systemMessage);

        // Пользовательское сообщение
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");

        List<Object> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", userPrompt));

        // Добавляем изображения, если есть
        if (!CollectionUtils.isEmpty(imageUrls)) {
            for (String imageUrl : imageUrls) {
                Map<String, Object> imageContent = new HashMap<>();
                imageContent.put("type", "image_url");
                imageContent.put("image_url", Map.of("url", imageUrl));
                content.add(imageContent);
            }
        }

        userMessage.put("content", content);
        messages.add(userMessage);

        requestBody.put("messages", messages);

        return requestBody;
    }

    /**
     * Извлечь улучшенный промпт из ответа DeepInfra API.
     */
    private String extractEnhancedPrompt(DeepInfraResponseDTO response) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            log.error("Ответ от DeepInfra API не содержит choices");
            throw new PromptEnhancementException("Ответ от DeepInfra API не содержит улучшенного промпта", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        DeepInfraResponseDTO.Choice firstChoice = response.getChoices().get(0);
        
        if (firstChoice.getMessage() == null) {
            log.error("Choice не содержит message");
            throw new PromptEnhancementException("Ответ от DeepInfra API не содержит сообщения", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String enhancedPrompt = firstChoice.getMessage().getContent();
        
        if (enhancedPrompt == null || enhancedPrompt.trim().isEmpty()) {
            log.error("Улучшенный промпт пуст, finish_reason: {}", firstChoice.getFinishReason());
            throw new PromptEnhancementException("Улучшенный промпт пуст", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // Удаляем reasoning content, если он присутствует
        // Вариант 1: <think>...</think>
        enhancedPrompt = enhancedPrompt.replaceAll("(?s)<think>.*?</think>\\s*", "");
        // Вариант 2: <reasoning>...</reasoning>
        enhancedPrompt = enhancedPrompt.replaceAll("(?s)<reasoning>.*?</reasoning>\\s*", "");
        
        String cleanedPrompt = enhancedPrompt.trim();
        
        if (cleanedPrompt.isEmpty()) {
            log.error("Улучшенный промпт пуст после очистки от reasoning content");
            throw new PromptEnhancementException("Улучшенный промпт пуст", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return cleanedPrompt;
    }

    /**
     * Преобразование ошибок WebClient в PromptEnhancementException.
     */
    private Throwable mapToPromptEnhancementException(Throwable e) {
        if (e instanceof PromptEnhancementException) {
            return e;
        }
        
        if (e instanceof WebClientRequestException) {
            return new PromptEnhancementException(
                    "Не удалось подключиться к сервису улучшения промптов. Проверьте интернет или попробуйте позже.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    e);
        } else if (e instanceof WebClientResponseException webE) {
            return new PromptEnhancementException(
                    String.format("Сервис улучшения промптов вернул ошибку. Статус: %s, причина: %s", 
                            webE.getStatusCode(), webE.getStatusText()),
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    e);
        } else {
            return new PromptEnhancementException(
                    "Произошла ошибка при улучшении промпта: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    e);
        }
    }
}

