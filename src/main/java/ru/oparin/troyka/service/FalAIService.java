package ru.oparin.troyka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.config.properties.FalAiProperties;
import ru.oparin.troyka.exception.FalAIException;
import ru.oparin.troyka.model.dto.fal.FalAIImageDTO;
import ru.oparin.troyka.model.dto.fal.FalAIResponseDTO;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.dto.fal.ImageRs;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static ru.oparin.troyka.model.dto.fal.OutputFormatEnum.JPEG;

@Slf4j
@Service
public class FalAIService {
    public static final String PREFIX_PATH = "/fal-ai/";

    private final WebClient webClient;
    private final FalAiProperties prop;
    private final ImageGenerationHistoryService imageGenerationHistoryService;

    public FalAIService(WebClient.Builder webClientBuilder, 
                       FalAiProperties falAiProperties,
                       ImageGenerationHistoryService imageGenerationHistoryService) {
        this.webClient = webClientBuilder
                .baseUrl(falAiProperties.getApi().getUrl())
.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Key " + falAiProperties.getApi().getKey())
                .build();
        this.prop = falAiProperties;
        this.imageGenerationHistoryService = imageGenerationHistoryService;
    }

    public Mono<ImageRs> getImageResponse(ImageRq rq) {
        String prompt = rq.getPrompt();
        Integer numImages = rq.getNumImages() == null ? 1 : rq.getNumImages();
        String outputFormat = rq.getOutputFormat() == null ? JPEG.name().toLowerCase() : rq.getOutputFormat().name().toLowerCase();
        Map<String, Object> requestBody = Map.of(
                "prompt", prompt,
                "num_images", numImages,
                "output_format", outputFormat
        );

        String fullModelPath = PREFIX_PATH + prop.getModel();
        String fullUrl = prop.getApi().getUrl() + fullModelPath;
        log.info("Будет отправлено сообщение в fal.ai по адресу '{}' с телом '{}'", fullUrl, requestBody);

        return webClient.post()
                .uri(fullModelPath)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<FalAIResponseDTO>() {
                })
                .timeout(Duration.ofSeconds(30))
                .map(this::extractImageResponse)
                .flatMap(response -> {
                    // Сохраняем первую ссылку на изображение в истории
                    if (!response.getImageUrls().isEmpty()) {
                        String imageUrl = response.getImageUrls().get(0);
                        return imageGenerationHistoryService.saveHistory(imageUrl, prompt)
                                .thenReturn(response);
                    }
                    return Mono.just(response);
                })
                .doOnSuccess(response -> log.info("Успешно получен ответ с изображением: {}", response))
                .onErrorResume(WebClientRequestException.class, e -> {
                    log.error("Ошибка подключения к {}: {}", fullUrl, e.getMessage());
                                        throw new FalAIException("Не удалось подключиться к сервису fal.ai. Проверьте подключение к интернету и доступность сервиса.", HttpStatus.SERVICE_UNAVAILABLE, e);
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Ошибка ответа от fal.ai: {}", e.getMessage());
                    log.error("Ответ сервера: {}", e.getResponseBodyAsString());
                    log.error("Статус: {}", e.getStatusCode());
                    throw new FalAIException("Сервис fal.ai вернул ошибку: " + e.getMessage() + ", статус: " + e.getStatusCode(), HttpStatus.UNPROCESSABLE_ENTITY, e);
})
                .onErrorResume(Exception.class, e -> {
                    log.error("Неизвестная ошибка при работе с fal.ai: ", e);
                    throw new FalAIException("Произошла ошибка при работе с сервисом fal.ai: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
});
    }

    private ImageRs extractImageResponse(FalAIResponseDTO response) {
        log.info("Получен ответ: {}", response);
        String description = response.getDescription();

        List<String> urls = response.getImages().stream()
                .map(FalAIImageDTO::getUrl)
                .toList();

        return new ImageRs(description, urls);
    }
}