package ru.oparin.troyka.mapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import ru.oparin.troyka.service.ImageCompressionService;
import ru.oparin.troyka.service.provider.ProviderConstants;

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
    private final ImageCompressionService imageCompressionService;

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
     * Создать запрос к LaoZhang API в формате Google Gemini API.
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
        boolean isNewImage = CollectionUtils.isEmpty(inputImageUrls);

        if (isNewImage) {
            // Для создания нового изображения - просто текстовый промпт
            List<LaoZhangRequestDTO.Part> parts = new ArrayList<>();
            parts.add(LaoZhangRequestDTO.Part.builder()
                    .text(finalPrompt)
                    .build());

            List<LaoZhangRequestDTO.Content> contents = new ArrayList<>();
            contents.add(LaoZhangRequestDTO.Content.builder()
                    .parts(parts)
                    .build());

            return Mono.just(buildRequest(contents, imageRq, resolution));
        } else {
            // Для редактирования - нужно передать изображения и промпт
            return convertImageUrlsToBase64ForGemini(inputImageUrls)
                    .map(imageDataList -> {
                        List<LaoZhangRequestDTO.Part> parts = new ArrayList<>();

                        // Добавляем изображения
                        for (ImageData imageData : imageDataList) {
                            parts.add(LaoZhangRequestDTO.Part.builder()
                                    .inlineData(LaoZhangRequestDTO.InlineData.builder()
                                            .mimeType(imageData.mimeType)
                                            .data(imageData.base64Data)
                                            .build())
                                    .build());
                        }

                        // Добавляем текстовый промпт
                        parts.add(LaoZhangRequestDTO.Part.builder()
                                .text(finalPrompt)
                                .build());

                        List<LaoZhangRequestDTO.Content> contents = new ArrayList<>();
                        contents.add(LaoZhangRequestDTO.Content.builder()
                                .parts(parts)
                                .build());

                        return buildRequest(contents, imageRq, resolution);
                    });
        }
    }

    /**
     * Построить запрос в формате Gemini API.
     */
    private LaoZhangRequestDTO buildRequest(List<LaoZhangRequestDTO.Content> contents,
                                            ImageRq imageRq, Resolution resolution) {
        LaoZhangRequestDTO.LaoZhangRequestDTOBuilder builder = LaoZhangRequestDTO.builder()
                .contents(contents);

        // Создаем generationConfig
        LaoZhangRequestDTO.GenerationConfig.GenerationConfigBuilder configBuilder = 
                LaoZhangRequestDTO.GenerationConfig.builder()
                        .responseModalities(List.of("IMAGE"));

        // Добавляем imageConfig для Pro версии (поддерживает 4K и кастомные соотношения сторон)
        // Для Standard версии тоже можно указать aspectRatio, но разрешение всегда 1K
        if (imageRq.getModel() == GenerationModelType.NANO_BANANA_PRO) {
            // Pro версия: поддерживает 1K, 2K, 4K и любые соотношения сторон
            LaoZhangRequestDTO.ImageConfig imageConfig = LaoZhangRequestDTO.ImageConfig.builder()
                    .aspectRatio(imageRq.getAspectRatio() != null ? imageRq.getAspectRatio() : "1:1")
                    .build();

            if (resolution != null) {
                imageConfig.setImageSize(resolution.getValue()); // 1K, 2K, 4K
            } else {
                imageConfig.setImageSize("1K"); // По умолчанию 1K
            }

            configBuilder.imageConfig(imageConfig);
        } else if (imageRq.getModel() == GenerationModelType.NANO_BANANA) {
            // Standard версия: только 1K, но можно указать aspectRatio
            // Если aspectRatio не 1:1, добавляем imageConfig
            String aspectRatio = imageRq.getAspectRatio() != null ? imageRq.getAspectRatio() : "1:1";
            if (!"1:1".equals(aspectRatio)) {
                LaoZhangRequestDTO.ImageConfig imageConfig = LaoZhangRequestDTO.ImageConfig.builder()
                        .aspectRatio(aspectRatio)
                        .imageSize("1K") // Standard всегда 1K
                        .build();
                configBuilder.imageConfig(imageConfig);
            }
        }

        builder.generationConfig(configBuilder.build());
        return builder.build();
    }

    /**
     * Вспомогательный класс для хранения данных изображения.
     */
    private static class ImageData {
        String mimeType;
        String base64Data; // Без префикса data:image/...;base64,

        ImageData(String mimeType, String base64Data) {
            this.mimeType = mimeType;
            this.base64Data = base64Data;
        }
    }

    /**
     * Конвертировать URL изображений в base64 для формата Gemini API.
     * Загружает изображения по URL, сжимает их при необходимости и конвертирует в base64 без префикса data:.
     *
     * @param imageUrls список URL изображений
     * @return список ImageData с MIME типом и base64 данными
     */
    private Mono<List<ImageData>> convertImageUrlsToBase64ForGemini(List<String> imageUrls) {
        if (CollectionUtils.isEmpty(imageUrls)) {
            return Mono.just(List.of());
        }

        WebClient webClient = webClientBuilder.build();

        return Flux.fromIterable(imageUrls)
                .index()
                .flatMap(tuple -> {
                    long index = tuple.getT1();
                    String url = tuple.getT2();
                    log.debug("Загрузка изображения {} для конвертации в base64: {}", index + 1, url);
                    return webClient.get()
                            .uri(url)
                            .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.parseMediaType("image/webp"))
                            .retrieve()
                            .bodyToMono(byte[].class)
                            .map(imageBytes -> {
                                try {
                                    // Определяем MIME тип по URL или по первым байтам
                                    String mimeType = detectMimeType(url, imageBytes);
                                    
                                    // Сжимаем изображение, если оно превышает лимит
                                    byte[] processedBytes = imageBytes;
                                    if (imageBytes.length > ProviderConstants.LaoZhang.MAX_SINGLE_IMAGE_SIZE_BYTES) {
                                        log.info("Изображение {} превышает лимит ({} bytes), начинаем сжатие", 
                                                index + 1, imageBytes.length);
                                        processedBytes = imageCompressionService.compressImage(imageBytes, mimeType);
                                        // После сжатия всегда JPEG
                                        mimeType = "image/jpeg";
                                        log.info("Изображение {} сжато: {} -> {} bytes", 
                                                index + 1, imageBytes.length, processedBytes.length);
                                    }
                                    
                                    // Base64 без префикса data: для Gemini API
                                    String base64 = Base64.getEncoder().encodeToString(processedBytes);
                                    return new ImageData(mimeType, base64);
                                } catch (Exception e) {
                                    log.error("Ошибка при обработке изображения {}: {}", url, e.getMessage(), e);
                                    throw new RuntimeException("Ошибка при обработке изображения: " + e.getMessage(), e);
                                }
                            })
                            .onErrorResume(error -> {
                                log.error("Ошибка при загрузке изображения {}: {}", url, error.getMessage());
                                return Mono.error(error);
                            });
                })
                .collectList()
                .flatMap(imageDataList -> {
                    // Проверяем общий размер запроса (base64 + промпт + метаданные)
                    long totalSize = estimateRequestSize(imageDataList);
                    log.debug("Оценка размера запроса: {} bytes (лимит: {} bytes)", 
                            totalSize, ProviderConstants.LaoZhang.MAX_REQUEST_BODY_SIZE_BYTES);
                    
                    if (totalSize > ProviderConstants.LaoZhang.MAX_REQUEST_BODY_SIZE_BYTES) {
                        log.warn("Общий размер запроса ({}) превышает лимит ({}). " +
                                "Это может привести к ошибке 413. Рекомендуется fallback на FAL_AI.", 
                                totalSize, ProviderConstants.LaoZhang.MAX_REQUEST_BODY_SIZE_BYTES);
                        // Не бросаем ошибку здесь, пусть провайдер попробует отправить
                        // Если будет 413, сработает fallback механизм
                    }
                    
                    log.debug("Конвертировано {} изображений в base64", imageDataList.size());
                    return Mono.just(imageDataList);
                });
    }

    /**
     * Оценить размер запроса в байтах (base64 изображения + промпт + метаданные).
     * Base64 увеличивает размер примерно на 33%, поэтому учитываем это.
     *
     * @param imageDataList список изображений
     * @return оценка размера в байтах
     */
    private long estimateRequestSize(List<ImageData> imageDataList) {
        long imagesSize = imageDataList.stream()
                .mapToLong(data -> data.base64Data.length())
                .sum();
        
        // Примерная оценка размера метаданных и промпта (JSON структура)
        // В реальности это будет больше, но для оценки достаточно
        long metadataSize = 2000; // ~2KB на метаданные
        
        return imagesSize + metadataSize;
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
