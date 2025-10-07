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
import ru.oparin.troyka.config.properties.FalAiProperties;
import ru.oparin.troyka.exception.FalAIException;
import ru.oparin.troyka.model.dto.fal.FalAIImageDTO;
import ru.oparin.troyka.model.dto.fal.FalAIResponseDTO;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.dto.fal.ImageRs;

import java.time.Duration;
import java.util.HashMap;
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
    private final UserPointsService userPointsService;

    public FalAIService(WebClient.Builder webClientBuilder,
                        FalAiProperties falAiProperties,
                        ImageGenerationHistoryService imageGenerationHistoryService,
                        UserPointsService userPointsService) {
        this.webClient = webClientBuilder
                .baseUrl(falAiProperties.getApi().getUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Key " + falAiProperties.getApi().getKey())
                .build();
        this.prop = falAiProperties;
        this.imageGenerationHistoryService = imageGenerationHistoryService;
        this.userPointsService = userPointsService;
    }

    public Mono<ImageRs> getImageResponse(ImageRq rq, Long userId) {
        // Проверяем, достаточно ли поинтов у пользователя (3 поинта за изображение)
        Integer numImages = rq.getNumImages() == null ? 1 : rq.getNumImages();
        Integer pointsNeeded = numImages * 3;

        return userPointsService.hasEnoughPoints(userId, pointsNeeded)
                .flatMap(hasEnough -> {
                    if (!hasEnough) {
                        return Mono.error(new FalAIException("Недостаточно поинтов для генерации изображений. Требуется: " + pointsNeeded, HttpStatus.PAYMENT_REQUIRED));
                    }

                    // Списываем поинты
                    return userPointsService.deductPointsFromUser(userId, pointsNeeded)
                            .then(Mono.defer(() -> {
                                String prompt = rq.getPrompt();
                                String outputFormat = rq.getOutputFormat() == null ? JPEG.name().toLowerCase() : rq.getOutputFormat().name().toLowerCase();
                                Map<String, Object> requestBody = new HashMap<>(Map.of(
                                        "prompt", prompt,
                                        "num_images", numImages,
                                        "output_format", outputFormat
                                ));

                                List<String> imageUrls = rq.getImageUrls();
                                boolean isNewImage = CollectionUtils.isEmpty(imageUrls);
                                if (!isNewImage) {
                                    requestBody.put("image_urls", imageUrls);
                                }

                                String model = isNewImage ? prop.getModel().getCreate() : prop.getModel().getEdit();
                                String fullModelPath = PREFIX_PATH + model;
                                String fullUrl = prop.getApi().getUrl() + fullModelPath;
                                String modelType = isNewImage ? "создание" : "редактирование";
                                log.info("Будет отправлен запрос в fal.ai на {} изображений по адресу '{}' с телом '{}'", modelType, fullUrl, requestBody);

                                return webClient.post()
                                        .uri(fullModelPath)
                                        .bodyValue(requestBody)
                                        .retrieve()
                                        .bodyToMono(new ParameterizedTypeReference<FalAIResponseDTO>() {
                                        })
                                        .timeout(Duration.ofSeconds(30))
                                        .map(this::extractImageResponse)
                                        .flatMap(response -> {
                                            // Сохраняем все ссылки на изображения в истории
                                            return imageGenerationHistoryService.saveHistories(response.getImageUrls(), prompt)
                                                    .then(Mono.just(response));
                                        })
                                        .doOnSuccess(response -> log.info("Успешно получен ответ с изображением: {}", response))
                                        .onErrorResume(WebClientRequestException.class, e -> {
                                            throw new FalAIException("Не удалось подключиться к сервису fal.ai. Проверьте подключение к интернету и доступность сервиса.", HttpStatus.SERVICE_UNAVAILABLE, e);
                                        })
                                        .onErrorResume(WebClientResponseException.class, e -> {
                                            throw new FalAIException("Сервис fal.ai вернул ошибку: " + e.getMessage() + ", статус: " + e.getStatusCode(), HttpStatus.UNPROCESSABLE_ENTITY, e);
                                        })
                                        .onErrorResume(Exception.class, e -> {
                                            throw new FalAIException("Произошла ошибка при работе с сервисом fal.ai: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
                                        });
                            }));
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