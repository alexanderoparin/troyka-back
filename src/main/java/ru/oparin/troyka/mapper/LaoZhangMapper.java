package ru.oparin.troyka.mapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.dto.laozhang.LaoZhangRequestDTO;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.Resolution;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Маппер для конвертации запросов в формат LaoZhang AI API.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LaoZhangMapper {

    private final WebClient.Builder webClientBuilder;

    /**
     * Получить имя модели LaoZhang для типа модели.
     *
     * @param modelType тип модели
     * @return имя модели LaoZhang
     */
    public String getLaoZhangModelName(GenerationModelType modelType) {
        return switch (modelType) {
            case NANO_BANANA -> "gemini-2.5-flash-image-preview";
            case NANO_BANANA_PRO -> "gemini-3-pro-image-preview";
        };
    }

    /**
     * Создать запрос к LaoZhang API.
     *
     * @param imageRq      запрос на генерацию
     * @param finalPrompt  финальный промпт (с учетом стиля)
     * @param numImages    количество изображений
     * @param inputImageUrls список URL входных изображений
     * @param resolution   разрешение
     * @return запрос к LaoZhang API
     */
    public Mono<LaoZhangRequestDTO> createRequest(ImageRq imageRq, String finalPrompt, 
                                                   Integer numImages, List<String> inputImageUrls, 
                                                   Resolution resolution) {
        String modelName = getLaoZhangModelName(imageRq.getModel());
        boolean isNewImage = CollectionUtils.isEmpty(inputImageUrls);

        // Создаем сообщения
        List<LaoZhangRequestDTO.Message> messages = new ArrayList<>();

        if (isNewImage) {
            // Для создания нового изображения - просто текстовый промпт
            messages.add(LaoZhangRequestDTO.Message.builder()
                    .role("user")
                    .content(finalPrompt)
                    .build());
            return Mono.just(buildRequest(modelName, messages, imageRq, resolution));
        } else {
            // Для редактирования - нужно передать изображения и промпт
            return convertImageUrlsToBase64(inputImageUrls)
                    .map(base64Images -> {
                        // Создаем массив content parts
                        List<LaoZhangRequestDTO.ContentPart> contentParts = new ArrayList<>();

                        // Добавляем изображения
                        for (String base64Image : base64Images) {
                            contentParts.add(LaoZhangRequestDTO.ContentPart.builder()
                                    .type("image_url")
                                    .imageUrl(LaoZhangRequestDTO.ImageUrl.builder()
                                            .url(base64Image) // base64 в формате data:image/...;base64,...
                                            .build())
                                    .build());
                        }

                        // Добавляем текстовый промпт
                        contentParts.add(LaoZhangRequestDTO.ContentPart.builder()
                                .type("text")
                                .text(finalPrompt)
                                .build());

                        LaoZhangRequestDTO.Message message = LaoZhangRequestDTO.Message.builder()
                                .role("user")
                                .content(contentParts)
                                .build();
                        
                        messages.add(message);
                        return buildRequest(modelName, messages, imageRq, resolution);
                    });
        }
    }

    /**
     * Построить запрос.
     */
    private LaoZhangRequestDTO buildRequest(String modelName, List<LaoZhangRequestDTO.Message> messages,
                                            ImageRq imageRq, Resolution resolution) {
        LaoZhangRequestDTO.LaoZhangRequestDTOBuilder builder = LaoZhangRequestDTO.builder()
                .model(modelName)
                .stream(false)
                .messages(messages);

        // Для Pro версии добавляем imageConfig с разрешением и aspect ratio
        if (imageRq.getModel() == GenerationModelType.NANO_BANANA_PRO) {
            LaoZhangRequestDTO.ImageConfig imageConfig = LaoZhangRequestDTO.ImageConfig.builder()
                    .aspectRatio(imageRq.getAspectRatio())
                    .build();

            if (resolution != null) {
                imageConfig.setImageSize(resolution.getValue());
            } else {
                imageConfig.setImageSize("1K"); // По умолчанию 1K
            }

            builder.imageConfig(imageConfig);
        }

        return builder.build();
    }

    /**
     * Конвертировать URL изображений в base64.
     * Загружает изображения по URL и конвертирует в base64.
     *
     * @param imageUrls список URL изображений
     * @return список base64 строк в формате data:image/...;base64,...
     */
    private Mono<List<String>> convertImageUrlsToBase64(List<String> imageUrls) {
        if (CollectionUtils.isEmpty(imageUrls)) {
            return Mono.just(List.of());
        }

        WebClient webClient = webClientBuilder.build();

        return Flux.fromIterable(imageUrls)
                .flatMap(url -> {
                    log.debug("Загрузка изображения для конвертации в base64: {}", url);
                    return webClient.get()
                            .uri(url)
                            .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.parseMediaType("image/webp"))
                            .retrieve()
                            .bodyToMono(byte[].class)
                            .map(imageBytes -> {
                                // Определяем MIME тип по URL или по первым байтам
                                String mimeType = detectMimeType(url, imageBytes);
                                String base64 = Base64.getEncoder().encodeToString(imageBytes);
                                return "data:" + mimeType + ";base64," + base64;
                            })
                            .onErrorResume(error -> {
                                log.error("Ошибка при загрузке изображения {}: {}", url, error.getMessage());
                                return Mono.error(error);
                            });
                })
                .collectList()
                .doOnNext(base64Images -> log.debug("Конвертировано {} изображений в base64", base64Images.size()));
    }

    /**
     * Определить MIME тип изображения.
     */
    private String detectMimeType(String url, byte[] imageBytes) {
        // Определяем по расширению URL
        String urlLower = url.toLowerCase();
        if (urlLower.contains(".jpg") || urlLower.contains(".jpeg")) {
            return "image/jpeg";
        } else if (urlLower.contains(".png")) {
            return "image/png";
        } else if (urlLower.contains(".webp")) {
            return "image/webp";
        }

        // Определяем по магическим байтам
        if (imageBytes.length >= 4) {
            // JPEG: FF D8 FF
            if (imageBytes[0] == (byte) 0xFF && imageBytes[1] == (byte) 0xD8 && imageBytes[2] == (byte) 0xFF) {
                return "image/jpeg";
            }
            // PNG: 89 50 4E 47
            if (imageBytes[0] == (byte) 0x89 && imageBytes[1] == (byte) 0x50 &&
                imageBytes[2] == (byte) 0x4E && imageBytes[3] == (byte) 0x47) {
                return "image/png";
            }
            // WEBP: RIFF...WEBP
            if (imageBytes.length >= 12 &&
                imageBytes[0] == 'R' && imageBytes[1] == 'I' && imageBytes[2] == 'F' && imageBytes[3] == 'F' &&
                imageBytes[8] == 'W' && imageBytes[9] == 'E' && imageBytes[10] == 'B' && imageBytes[11] == 'P') {
                return "image/webp";
            }
        }

        // По умолчанию JPEG
        return "image/jpeg";
    }
}
