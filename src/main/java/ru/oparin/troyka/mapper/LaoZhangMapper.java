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
     * @param imageRq        запрос на генерацию
     * @param finalPrompt    финальный промпт (с учетом стиля)
     * @param inputImageUrls список URL входных изображений
     * @param resolution     разрешение
     * @return запрос к LaoZhang API и опциональное предупреждение при сжатии
     */
    public Mono<LaoZhangRequestResult> createRequest(ImageRq imageRq, String finalPrompt,
                                                   List<String> inputImageUrls,
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

            return Mono.just(new LaoZhangRequestResult(buildRequest(contents, imageRq, resolution), null));
        } else {
            // Для редактирования - нужно передать изображения и промпт
            return convertImageUrlsToBase64ForGemini(inputImageUrls)
                    .map(convertResult -> {
                        List<LaoZhangRequestDTO.Part> parts = new ArrayList<>();
                        for (ImageData imageData : convertResult.list) {
                            parts.add(LaoZhangRequestDTO.Part.builder()
                                    .inlineData(LaoZhangRequestDTO.InlineData.builder()
                                            .mimeType(imageData.mimeType)
                                            .data(imageData.base64Data)
                                            .build())
                                    .build());
                        }
                        parts.add(LaoZhangRequestDTO.Part.builder()
                                .text(finalPrompt)
                                .build());

                        List<LaoZhangRequestDTO.Content> contents = new ArrayList<>();
                        contents.add(LaoZhangRequestDTO.Content.builder()
                                .parts(parts)
                                .build());

                        return new LaoZhangRequestResult(buildRequest(contents, imageRq, resolution), convertResult.warningMessage);
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
     * Результат создания запроса: сам запрос и опциональное предупреждение для пользователя (если было сжатие).
     */
    public static class LaoZhangRequestResult {
        private final LaoZhangRequestDTO request;
        private final String warningMessage;

        public LaoZhangRequestResult(LaoZhangRequestDTO request, String warningMessage) {
            this.request = request;
            this.warningMessage = warningMessage;
        }

        public LaoZhangRequestDTO getRequest() { return request; }
        public String getWarningMessage() { return warningMessage; }
    }

    /**
     * Вспомогательный класс для хранения данных изображения.
     */
    private static class ImageData {
        String mimeType;
        String base64Data; // Без префикса data:image/...;base64,
        boolean wasCompressed; // сжато из-за лимита 7 MB на файл

        ImageData(String mimeType, String base64Data) {
            this.mimeType = mimeType;
            this.base64Data = base64Data;
            this.wasCompressed = false;
        }

        ImageData(String mimeType, String base64Data, boolean wasCompressed) {
            this.mimeType = mimeType;
            this.base64Data = base64Data;
            this.wasCompressed = wasCompressed;
        }
    }

    /** Результат конвертации URL в base64: список изображений и предупреждение при сжатии. */
    private static class ConvertResult {
        final List<ImageData> list;
        final String warningMessage;

        ConvertResult(List<ImageData> list, String warningMessage) {
            this.list = list;
            this.warningMessage = warningMessage;
        }
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
                                        log.info("Изображение {} превышает лимит ({} bytes), сжимаем перед отправкой", 
                                                index + 1, imageBytes.length);
                                        processedBytes = imageCompressionService.compressImage(imageBytes, mimeType);
                                        mimeType = "image/jpeg";
                                        compressed = true;
                                        log.info("Изображение {} сжато: {} -> {} bytes", 
                                                index + 1, imageBytes.length, processedBytes.length);
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
                        // Дожимаем каждое изображение, чтобы весь запрос уложился в 20 MB
                        long maxBase64PerImage = (ProviderConstants.LaoZhang.MAX_REQUEST_BODY_SIZE_BYTES - 2000) / imageDataList.size();
                        long maxDecodedPerImage = (long) (maxBase64PerImage * 0.75);
                        log.info("Общий размер запроса {} bytes превышает лимит 20 MB. Дожимаем каждое изображение до ~{} bytes.", totalSize, maxDecodedPerImage);
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
