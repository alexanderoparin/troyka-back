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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сервис для улучшения промптов через DeepInfra API.
 * Использует Llama 3.1 8B Instruct для текстовых промптов (без изображений).
 * Для промптов с изображениями: сначала Gemini 2.5 Flash, при ошибках - fallback на Qwen2.5-VL-32B-Instruct.
 */
@Slf4j
@Service
public class PromptEnhancementService {

    private static final String ENDPOINT = "/openai/chat/completions";
    private static final String CONTENT_FILTER_ERROR_MESSAGE = 
            "Промпт или изображение были заблокированы фильтром безопасности. " +
            "Попробуйте изменить текст промпта или загрузить другое изображение.";
    private static final String FINISH_REASON_CONTENT_FILTER = "content_filter";

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
     * Использует retry с увеличенным лимитом токенов при нехватке токенов и fallback на оригинальный промпт.
     * Для промптов с изображениями: сначала пытается Gemini 2.5 Flash, при ошибках - fallback на Qwen2.5-VL-32B-Instruct.
     *
     * @param userPrompt исходный промпт пользователя
     * @param imageUrls опциональный список URL изображений для анализа
     * @param userStyle стиль пользователя для контекста улучшения
     * @return улучшенный промпт
     */
    public Mono<String> enhancePrompt(String userPrompt, List<String> imageUrls, ArtStyle userStyle) {
        String systemPrompt = buildSystemPrompt(userStyle);
        
        // Выбираем модель и параметры в зависимости от наличия изображений
        boolean hasImages = !CollectionUtils.isEmpty(imageUrls);
        
        if (hasImages) {
            // Для промптов с изображениями: сначала Gemini, при ошибках - fallback на Qwen
            DeepInfraProperties.ModelConfig primaryModel = properties.getGemini();
            DeepInfraProperties.ModelConfig fallbackModel = properties.getQwen25Vl();
            
            return enhancePromptWithModel(systemPrompt, userPrompt, imageUrls, primaryModel, false)
                    .onErrorResume(error -> {
                        log.warn("Ошибка при использовании модели {}: {}. Переключаемся на Qwen как fallback.", 
                                primaryModel.getModel(), error.getMessage());
                        return enhancePromptWithModel(systemPrompt, userPrompt, imageUrls, fallbackModel, true);
                    });
        } else {
            // Для текстовых промптов используем Llama
            return enhancePromptWithModel(systemPrompt, userPrompt, imageUrls, properties.getLlama(), false);
        }
    }
    
    /**
     * Улучшить промпт с использованием указанной модели.
     * 
     * @param systemPrompt системный промпт
     * @param userPrompt промпт пользователя
     * @param imageUrls список URL изображений
     * @param modelConfig конфигурация модели
     * @param isFallback флаг, указывающий что это fallback запрос
     * @return улучшенный промпт
     */
    private Mono<String> enhancePromptWithModel(String systemPrompt, String userPrompt, List<String> imageUrls,
                                                DeepInfraProperties.ModelConfig modelConfig, boolean isFallback) {
        return makeEnhancementRequest(systemPrompt, userPrompt, imageUrls, modelConfig, modelConfig.getMaxTokens())
                .flatMap(response -> {
                    DeepInfraResponseDTO.Choice firstChoice = response.getChoices().get(0);
                    String finishReason = firstChoice.getFinishReason();
                    String enhancedPrompt = extractEnhancedPrompt(response, false);
                    
                    // Если content_filter - выбрасываем исключение с понятным сообщением для пользователя
                    if (FINISH_REASON_CONTENT_FILTER.equals(finishReason)) {
                        return handleContentFilter(userPrompt, imageUrls);
                    }
                    
                    // Если промпт пустой, возвращаем оригинальный как fallback
                    if (isPromptEmpty(enhancedPrompt)) {
                        log.warn("Улучшенный промпт пуст (finish_reason={}). Возвращаем оригинальный промпт как fallback.", finishReason);
                        return Mono.just(userPrompt);
                    }
                    
                    return Mono.just(enhancedPrompt);
                })
                .onErrorMap(error -> {
                    // Если это fallback запрос, не преобразуем ошибку - пусть она пробросится дальше
                    if (isFallback) {
                        return error;
                    }
                    // Для основного запроса преобразуем ошибку для последующего fallback
                    return mapToPromptEnhancementException(error);
                });
    }
    
    /**
     * Выполнить запрос на улучшение промпта с указанным лимитом токенов.
     */
    private Mono<DeepInfraResponseDTO> makeEnhancementRequest(String systemPrompt, String userPrompt, 
                                                             List<String> imageUrls, 
                                                             DeepInfraProperties.ModelConfig modelConfig,
                                                             Integer maxTokens) {
        Map<String, Object> requestBody = buildRequestBody(systemPrompt, userPrompt, imageUrls, modelConfig, maxTokens);
        log.debug("Отправляем запрос на улучшение промпта в model = {} с max_tokens = {}: {}", 
                modelConfig.getModel(), maxTokens, requestBody);

        return webClient.post()
                .uri(ENDPOINT)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<DeepInfraResponseDTO>() {})
                .timeout(Duration.ofMillis(properties.getTimeout()))
                .doOnNext(response -> log.info("Полный ответ от DeepInfra API: {}", response));
    }

    /**
     * Построить системный промпт с учетом стиля пользователя
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
    private Map<String, Object> buildRequestBody(String systemPrompt, String userPrompt, List<String> imageUrls, 
                                                  DeepInfraProperties.ModelConfig modelConfig, Integer maxTokens) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelConfig.getModel());
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", modelConfig.getTemperature());

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
     * 
     * @param response ответ от DeepInfra API
     * @param isRetry флаг, указывающий что это повторная попытка после нехватки токенов
     * @return улучшенный промпт или null/пустую строку, если промпт пустой
     */
    private String extractEnhancedPrompt(DeepInfraResponseDTO response, boolean isRetry) {
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
        String finishReason = firstChoice.getFinishReason();
        
        // Если промпт пустой, возвращаем null или пустую строку (fallback будет обработан в enhancePrompt)
        if (isPromptEmpty(enhancedPrompt)) {
            if (isRetry) {
                log.warn("Улучшенный промпт пуст даже после retry (finish_reason={})", finishReason);
            } else {
                log.warn("Улучшенный промпт пуст (finish_reason={})", finishReason);
            }
            return enhancedPrompt; // Может быть null или пустая строка
        }

        // Извлекаем промпт из reasoning блока, если он там находится
        // Вариант 1: Ищем паттерн с кавычками - текст в кавычках после ключевых фраз
        // Учитываем возможные переносы строк между фразой и кавычкой
        Pattern reasoningPatternWithQuotes = Pattern.compile(
            "(?s).*?(?:the revised prompt will be|revised prompt will be|revised prompt|here's the final prompt|here is the final prompt|final prompt will be|the final prompt):\\s*\"([^\"]+)\".*?",
            Pattern.CASE_INSENSITIVE
        );
        Matcher reasoningMatcherWithQuotes = reasoningPatternWithQuotes.matcher(enhancedPrompt);
        
        if (reasoningMatcherWithQuotes.find()) {
            // Нашли промпт внутри reasoning блока в кавычках
            enhancedPrompt = reasoningMatcherWithQuotes.group(1);
        } else {
            // Вариант 2: Ищем текст после ключевых фраз до закрывающего тега reasoning
            // Это для случаев, когда промпт не в кавычках или кавычки многострочные
            Pattern reasoningPatternExtract = Pattern.compile(
                "(?s).*?(?:here's the final prompt|here is the final prompt|the final prompt|final prompt):\\s*(.+?)(?=\\s*</think>|\\s*</reasoning>|$)",
                Pattern.CASE_INSENSITIVE
            );
            Matcher reasoningMatcherExtract = reasoningPatternExtract.matcher(enhancedPrompt);
            
            if (reasoningMatcherExtract.find()) {
                // Нашли промпт после фразы
                String extractedPrompt = reasoningMatcherExtract.group(1).trim();
                // Удаляем возможные кавычки в начале и конце
                if (extractedPrompt.startsWith("\"") && extractedPrompt.endsWith("\"")) {
                    extractedPrompt = extractedPrompt.substring(1, extractedPrompt.length() - 1).trim();
                }
                // Удаляем возможные переносы строк в начале и конце
                extractedPrompt = extractedPrompt.replaceAll("^\\s*[\\r\\n]+", "").replaceAll("[\\r\\n]+\\s*$", "");
                enhancedPrompt = extractedPrompt;
            } else {
                // Удаляем reasoning content, если он присутствует
                // Вариант 1: <think>...</think>
                enhancedPrompt = enhancedPrompt.replaceAll("(?s)<think>.*?</think>\\s*", "");
                // Вариант 2: <reasoning>...</reasoning>
                enhancedPrompt = enhancedPrompt.replaceAll("(?s)<reasoning>.*?</reasoning>\\s*", "");
            }
        }
        
        String cleanedPrompt = enhancedPrompt.trim();
        
        if (cleanedPrompt.isEmpty()) {
            log.warn("Улучшенный промпт пуст после очистки от reasoning content");
            return null; // Возвращаем null для fallback на оригинальный промпт
        }

        return cleanedPrompt;
    }

    /**
     * Проверить, является ли промпт пустым.
     */
    private boolean isPromptEmpty(String prompt) {
        return prompt == null || prompt.trim().isEmpty();
    }

    /**
     * Обработать ситуацию с content_filter - выбрасывает исключение с понятным сообщением.
     */
    private Mono<String> handleContentFilter(String userPrompt, List<String> imageUrls) {
        log.warn("Модель заблокировала ответ фильтром безопасности (content_filter). Промпт: '{}', изображений: {}", 
                userPrompt, imageUrls != null ? imageUrls.size() : 0);
        return Mono.error(new PromptEnhancementException(CONTENT_FILTER_ERROR_MESSAGE, HttpStatus.UNPROCESSABLE_ENTITY));
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
            String responseBody = webE.getResponseBodyAsString();
            log.error("DeepInfra API вернул ошибку. Статус: {}, тело ответа: {}", webE.getStatusCode(), responseBody);
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

