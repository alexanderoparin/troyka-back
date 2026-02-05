package ru.oparin.troyka.mapper;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
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

    private static final double BYTES_PER_MB = 1024.0 * 1024.0;
    /**
     * Оценка размера метаданных запроса (JSON, промпт) в байтах.
     */
    private static final long ESTIMATED_METADATA_BYTES = 2000L;
    /**
     * Коэффициент: декодированный размер ≈ base64.length * 0.75.
     */
    private static final double BASE64_TO_RAW_RATIO = 0.75;

    private final WebClient.Builder webClientBuilder;
    private final ImageCompressionService imageCompressionService;

    private static String formatMb(long bytes) {
        return String.format("%.2f", bytes / BYTES_PER_MB);
    }

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
     * @param imageRq        запрос на генерацию
     * @param finalPrompt    финальный промпт (с учетом стиля)
     * @param inputImageUrls список URL входных изображений
     * @param resolution     разрешение
     * @return запрос к LaoZhang API и опциональное предупреждение при сжатии
     */
    public Mono<LaoZhangRequestResult> createRequest(ImageRq imageRq, String finalPrompt,
                                                     List<String> inputImageUrls,
                                                     Resolution resolution) {
        if (CollectionUtils.isEmpty(inputImageUrls)) {
            List<LaoZhangRequestDTO.Content> contents = buildContentsForNewImage(finalPrompt);
            return Mono.just(new LaoZhangRequestResult(buildRequest(contents, imageRq, resolution), null));
        }
        return convertImageUrlsToBase64ForGemini(inputImageUrls)
                .map(convertResult -> {
                    List<LaoZhangRequestDTO.Content> contents = buildContentsForEdit(convertResult.list, finalPrompt);
                    return new LaoZhangRequestResult(buildRequest(contents, imageRq, resolution), convertResult.warningMessage);
                });
    }

    private List<LaoZhangRequestDTO.Content> buildContentsForNewImage(String finalPrompt) {
        List<LaoZhangRequestDTO.Part> parts = List.of(LaoZhangRequestDTO.Part.builder().text(finalPrompt).build());
        return List.of(LaoZhangRequestDTO.Content.builder().parts(parts).build());
    }

    private List<LaoZhangRequestDTO.Content> buildContentsForEdit(List<ImageData> imageDataList, String finalPrompt) {
        List<LaoZhangRequestDTO.Part> parts = new ArrayList<>();
        for (ImageData imageData : imageDataList) {
            parts.add(LaoZhangRequestDTO.Part.builder()
                    .inlineData(LaoZhangRequestDTO.InlineData.builder()
                            .mimeType(imageData.mimeType)
                            .data(imageData.base64Data)
                            .build())
                    .build());
        }
        parts.add(LaoZhangRequestDTO.Part.builder().text(finalPrompt).build());
        return List.of(LaoZhangRequestDTO.Content.builder().parts(parts).build());
    }

    /**
     * Построить запрос в формате Gemini API.
     */
    private LaoZhangRequestDTO buildRequest(List<LaoZhangRequestDTO.Content> contents,
                                            ImageRq imageRq, Resolution resolution) {
        LaoZhangRequestDTO.GenerationConfig config = LaoZhangRequestDTO.GenerationConfig.builder()
                .responseModalities(List.of("IMAGE"))
                .imageConfig(buildImageConfig(imageRq, resolution))
                .build();
        return LaoZhangRequestDTO.builder()
                .contents(contents)
                .generationConfig(config)
                .build();
    }

    private LaoZhangRequestDTO.ImageConfig buildImageConfig(ImageRq imageRq, Resolution resolution) {
        String aspectRatio = imageRq.getAspectRatio() != null ? imageRq.getAspectRatio() : "1:1";

        if (imageRq.getModel() == GenerationModelType.NANO_BANANA_PRO) {
            LaoZhangRequestDTO.ImageConfig config = LaoZhangRequestDTO.ImageConfig.builder()
                    .aspectRatio(aspectRatio)
                    .build();
            config.setImageSize(resolution != null ? resolution.getValue() : "1K");
            return config;
        }
        if (imageRq.getModel() == GenerationModelType.NANO_BANANA && !"1:1".equals(aspectRatio)) {
            return LaoZhangRequestDTO.ImageConfig.builder()
                    .aspectRatio(aspectRatio)
                    .imageSize("1K")
                    .build();
        }
        return null;
    }

    /**
     * Результат создания запроса: сам запрос и опциональное предупреждение для пользователя (если было сжатие).
     */
    @Value
    public static class LaoZhangRequestResult {
        LaoZhangRequestDTO request;
        String warningMessage;
    }

    /**
     * Вспомогательный класс для хранения данных изображения (MIME, base64 без префикса, флаг сжатия).
     */
    @Getter
    @AllArgsConstructor
    private static class ImageData {
        String mimeType;
        String base64Data;
        boolean wasCompressed;
    }

    /**
     * Результат конвертации URL в base64: список изображений и предупреждение при сжатии.
     */
    @Value
    private static class ConvertResult {
        List<ImageData> list;
        String warningMessage;
    }

    /**
     * Конвертировать URL изображений в base64 для формата Gemini API.
     * Загружает изображения по URL, сжимает их при необходимости и конвертирует в base64 без префикса data:.
     *
     * @param imageUrls список URL изображений
     * @return ConvertResult со списком ImageData и опциональным предупреждением при сжатии
     */
    private Mono<ConvertResult> convertImageUrlsToBase64ForGemini(List<String> imageUrls) {
        if (CollectionUtils.isEmpty(imageUrls)) {
            return Mono.just(new ConvertResult(List.of(), null));
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

                                    // Сжимаем изображение, если оно превышает лимит 7 MB на файл
                                    byte[] processedBytes = imageBytes;
                                    boolean compressed = false;
                                    if (imageBytes.length > ProviderConstants.LaoZhang.MAX_SINGLE_IMAGE_SIZE_BYTES) {
                                        log.info("Изображение {} превышает лимит ({} MB), сжимаем перед отправкой",
                                                index + 1, formatMb(imageBytes.length));
                                        processedBytes = imageCompressionService.compressImage(imageBytes, mimeType);
                                        mimeType = "image/jpeg";
                                        compressed = true;
                                        log.info("Изображение {} сжато: {} MB -> {} MB",
                                                index + 1, formatMb(imageBytes.length), formatMb(processedBytes.length));
                                    }

                                    String base64 = Base64.getEncoder().encodeToString(processedBytes);
                                    return new ImageData(mimeType, base64, compressed);
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
                    // Лимиты LaoZhang: один файл — макс. 7 MB, весь request body — макс. 20 MB
                    long totalSize = estimateRequestSize(imageDataList);
                    boolean hadSingleCompression = imageDataList.stream().anyMatch(d -> d.wasCompressed);
                    List<ImageData> resultList = imageDataList;
                    String warningMessage = null;

                    if (totalSize > ProviderConstants.LaoZhang.MAX_REQUEST_BODY_SIZE_BYTES) {
                        long maxBase64PerImage = (ProviderConstants.LaoZhang.MAX_REQUEST_BODY_SIZE_BYTES - ESTIMATED_METADATA_BYTES) / imageDataList.size();
                        long maxDecodedPerImage = (long) (maxBase64PerImage * BASE64_TO_RAW_RATIO);
                        log.info("Общий размер запроса {} MB превышает лимит 20 MB. Дожимаем каждое изображение до ~{} MB.",
                                formatMb(totalSize), formatMb(maxDecodedPerImage));
                        List<ImageData> compressedList = new ArrayList<>();
                        for (ImageData data : imageDataList) {
                            try {
                                byte[] decoded = Base64.getDecoder().decode(data.base64Data);
                                byte[] compressed = imageCompressionService.compressImageToMaxSize(decoded, data.mimeType, maxDecodedPerImage);
                                String base64 = Base64.getEncoder().encodeToString(compressed);
                                compressedList.add(new ImageData("image/jpeg", base64, false));
                            } catch (Exception e) {
                                log.warn("Не удалось дожать изображение для лимита 20 MB: {}", e.getMessage());
                                compressedList.add(data);
                            }
                        }
                        resultList = compressedList;
                        warningMessage = "Изображения сжаты для соответствия лимитам запроса (7 MB на файл, 20 MB на весь запрос).";
                    } else if (hadSingleCompression) {
                        warningMessage = "Изображения сжаты для соответствия лимиту запроса (7 MB на файл).";
                    }

                    log.debug("Конвертировано {} изображений в base64", resultList.size());
                    return Mono.just(new ConvertResult(resultList, warningMessage));
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
        return imagesSize + ESTIMATED_METADATA_BYTES;
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
