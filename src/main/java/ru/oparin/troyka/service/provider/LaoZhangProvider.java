package ru.oparin.troyka.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.config.properties.LaoZhangProperties;
import ru.oparin.troyka.exception.FalAIException;
import ru.oparin.troyka.mapper.LaoZhangMapper;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.dto.fal.ImageRs;
import ru.oparin.troyka.model.dto.laozhang.LaoZhangRequestDTO;
import ru.oparin.troyka.model.dto.laozhang.LaoZhangResponseDTO;
import ru.oparin.troyka.model.entity.ArtStyle;
import ru.oparin.troyka.model.entity.Session;
import ru.oparin.troyka.model.entity.User;
import ru.oparin.troyka.model.entity.UserPoints;
import ru.oparin.troyka.model.enums.GenerationModelType;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.model.enums.Resolution;
import ru.oparin.troyka.service.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Провайдер генерации изображений через LaoZhang AI.
 * Использует Google Native Format API для поддержки 4K разрешения и кастомных соотношений сторон.
 * <p>
 * Особенности:
 * <ul>
 *   <li>Синхронная генерация (без очереди)</li>
 *   <li>Поддержка генерации и редактирования изображений</li>
 *   <li>Автоматический возврат поинтов при ошибках или отмене запроса</li>
 *   <li>Увеличенные таймауты для поддержки генерации 4K (до 12 минут)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LaoZhangProvider implements ImageGenerationProvider {

    private final WebClient.Builder webClientBuilder;
    private final LaoZhangProperties laoZhangProperties;
    private final GenerationProperties generationProperties;
    private final LaoZhangMapper laoZhangMapper;
    private final Base64ImageService base64ImageService;
    private final UserPointsService userPointsService;
    private final SessionService sessionService;
    private final ArtStyleService artStyleService;
    private final ImageGenerationHistoryService imageGenerationHistoryService;
    private final UserService userService;
    private final ProviderErrorHandler errorHandler;
    private final ObjectMapper objectMapper;

    private WebClient webClient;

    @Override
    public Mono<ImageRs> generateImage(ImageRq request, Long userId) {
        log.debug("LaoZhangProvider: генерация изображения для пользователя {}", userId);

        GenerationContext context = new GenerationContext(request, userId);
        context.pointsNeeded = calculatePointsNeeded(request);

        return validatePoints(context)
                .flatMap(this::prepareContext)
                .flatMap(this::deductPoints)
                .flatMap(this::executeGeneration)
                .flatMap(this::saveImages)
                .flatMap(this::saveHistory)
                .flatMap(this::updateSession)
                .map(this::createResponse)
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("Цепочка генерации вернула пустой Mono для userId={}", userId);
                    return Mono.error(new IllegalStateException("Генерация не вернула результат"));
                }))
                .onErrorResume(error -> errorHandler.handleError(userId, error, context.pointsNeeded, context.pointsDeducted));
    }

    /**
     * Валидировать наличие достаточного количества поинтов у пользователя.
     */
    private Mono<GenerationContext> validatePoints(GenerationContext context) {
        return userPointsService.hasEnoughPoints(context.userId, context.pointsNeeded)
                .flatMap(hasEnough -> {
                    if (!hasEnough) {
                        String message = String.format(
                                ProviderConstants.ErrorMessages.INSUFFICIENT_POINTS,
                                context.pointsNeeded
                        );
                        return Mono.error(new FalAIException(message, HttpStatus.PAYMENT_REQUIRED));
                    }
                    return Mono.just(context);
                });
    }

    /**
     * Подготовить контекст генерации: получить сессию, стиль и пользователя.
     */
    private Mono<GenerationContext> prepareContext(GenerationContext context) {
        return sessionService.getOrCreateSession(context.request.getSessionId(), context.userId)
                .flatMap(session -> {
                    if (session == null) {
                        log.error("Сессия null для userId={}", context.userId);
                        return Mono.error(new IllegalStateException("Сессия не может быть null"));
                    }
                    context.session = session;
                    return artStyleService.getStyleById(context.request.getStyleId());
                })
                .flatMap(style -> {
                    if (style == null) {
                        log.error("Стиль null для userId={}, styleId={}", context.userId, context.request.getStyleId());
                        return Mono.error(new IllegalStateException("Стиль не может быть null"));
                    }
                    context.style = style;
                    return userService.findByIdOrThrow(context.userId);
                })
                .map(user -> {
                    if (user == null) {
                        log.error("Пользователь null для userId={}", context.userId);
                        throw new IllegalStateException("Пользователь не может быть null");
                    }
                    context.user = user;
                    context.finalPrompt = buildFinalPrompt(context.request.getPrompt(), context.style);
                    return context;
                });
    }

    /**
     * Построить финальный промпт с добавлением стиля.
     */
    private String buildFinalPrompt(String userPrompt, ArtStyle style) {
        return userPrompt + ", " + style.getPrompt();
    }

    /**
     * Списать поинты у пользователя.
     */
    private Mono<GenerationContext> deductPoints(GenerationContext context) {
        return userPointsService.deductPointsFromUser(context.userId, context.pointsNeeded)
                .map(userPoints -> {
                    context.userPoints = userPoints;
                    context.pointsDeducted = true; // Отмечаем, что поинты были списаны
                    return context;
                });
    }

    /**
     * Выполнить генерацию изображений через LaoZhang API.
     */
    private Mono<GenerationContext> executeGeneration(GenerationContext context) {
        String endpoint = buildEndpoint(context.request.getModel());
        logGenerationRequest(context, endpoint);

        return createLaoZhangRequest(context)
                .doOnNext(request -> {
                    // Логируем JSON запроса к LaoZhang API (без больших base64 данных)
                    try {
                        LaoZhangRequestDTO loggableRequest = createLoggableRequest(request);
                        String requestJson = objectMapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(loggableRequest);
                        log.info("=== ЗАПРОС В LAOZHANG API для userId={}, endpoint={} ===\n{}", 
                                context.userId, endpoint, requestJson);
                    } catch (Exception e) {
                        log.warn("Не удалось сериализовать LaoZhangRequest для логирования: {}", e.getMessage());
                    }
                })
                .flatMap(laoZhangRequest -> sendApiRequest(endpoint, laoZhangRequest, context))
                .doOnNext(response -> log.info("Получен ответ от LaoZhang API для userId={}, response null={}", 
                        context.userId, response == null))
                .flatMap(this::extractBase64Images)
                .map(base64Images -> {
                    context.base64Images = base64Images;
                    return context;
                });
    }

    /**
     * Построить endpoint для API запроса.
     */
    private String buildEndpoint(GenerationModelType modelType) {
        String modelName = laoZhangMapper.getLaoZhangModelName(modelType);
        return String.format(ProviderConstants.LaoZhang.ENDPOINT_TEMPLATE, modelName);
    }

    /**
     * Логировать информацию о запросе генерации.
     */
    private void logGenerationRequest(GenerationContext context, String endpoint) {
        boolean isNewImage = CollectionUtils.isEmpty(context.request.getInputImageUrls());
        String operationType = isNewImage ? "создание" : "редактирование";
        log.info("Будет отправлен запрос в LaoZhang AI на {} изображений (операция: {}), endpoint: {}",
                context.request.getNumImages(), operationType, endpoint);
    }

    /**
     * Создать запрос для LaoZhang API.
     */
    private Mono<LaoZhangRequestDTO> createLaoZhangRequest(GenerationContext context) {
        return laoZhangMapper.createRequest(
                context.request,
                context.finalPrompt,
                context.request.getNumImages(),
                context.request.getInputImageUrls(),
                context.request.getResolution()
        );
    }

    /**
     * Отправить запрос к LaoZhang API.
     */
    private Mono<LaoZhangResponseDTO> sendApiRequest(String endpoint, LaoZhangRequestDTO request,
                                                      GenerationContext context) {
        Mono<LaoZhangResponseDTO> apiRequest = getWebClient().post()
                .uri(endpoint)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<LaoZhangResponseDTO>() {})
                .timeout(ProviderConstants.LaoZhang.REQUEST_TIMEOUT)
                .doOnError(error -> {
                    log.error("Ошибка при получении ответа от LaoZhang API для userId={}, endpoint={}: {}",
                            context.userId, endpoint, error.getMessage(), error);
                });

        return apiRequest.doOnCancel(() ->
                errorHandler.handleCancellation(context.userId, context.pointsNeeded)
        );
    }


    /**
     * Сохранить изображения и получить их URL.
     */
    private Mono<GenerationContext> saveImages(GenerationContext context) {
        if (context.base64Images == null || context.base64Images.isEmpty()) {
            log.error("base64Images == null или пустой в saveImages для userId={}", context.userId);
            return Mono.error(new IllegalStateException("base64Images не может быть null или пустым"));
        }
        if (context.user == null) {
            log.error("user == null в saveImages для userId={}", context.userId);
            return Mono.error(new IllegalStateException("user не может быть null"));
        }
        String username = context.user.getUsername();
        return Flux.fromIterable(context.base64Images)
                .flatMap(base64Image -> base64ImageService.saveBase64ImageAndGetUrl(
                        base64Image, username, ProviderConstants.LaoZhang.IMAGE_SUBDIRECTORY))
                .collectList()
                .map(imageUrls -> {
                    if (imageUrls == null || imageUrls.isEmpty()) {
                        log.error("Сохранение изображений вернуло null или пустой список для userId={}", context.userId);
                        throw new IllegalStateException("Не удалось сохранить изображения");
                    }
                    context.imageUrls = imageUrls;
                    return context;
                });
    }

    /**
     * Сохранить историю генерации.
     */
    private Mono<GenerationContext> saveHistory(GenerationContext context) {
        return imageGenerationHistoryService.saveHistories(
                context.userId,
                context.imageUrls,
                context.request.getPrompt(),
                context.session.getId(),
                context.request.getInputImageUrls(),
                context.request.getStyleId(),
                context.request.getAspectRatio(),
                context.request.getModel(),
                context.request.getResolution(),
                GenerationProvider.LAOZHANG_AI
        ).then(Mono.just(context));
    }

    /**
     * Обновить временную метку сессии.
     */
    private Mono<GenerationContext> updateSession(GenerationContext context) {
        return sessionService.updateSessionTimestamp(context.session.getId())
                .then(Mono.just(context));
    }

    /**
     * Создать ответ с изображениями.
     */
    private ImageRs createResponse(GenerationContext context) {
        if (context.userPoints == null) {
            log.error("userPoints == null в createResponse для userId={}", context.userId);
            throw new IllegalStateException("userPoints не может быть null");
        }
        if (context.imageUrls == null || context.imageUrls.isEmpty()) {
            log.error("imageUrls == null или пустой в createResponse для userId={}", context.userId);
            throw new IllegalStateException("imageUrls не может быть null или пустым");
        }
        Integer balance = context.userPoints.getPoints();
        log.debug("Создание ImageRs для userId={}, imageUrls count={}, balance={}", 
                context.userId, context.imageUrls.size(), balance);
        return new ImageRs(context.imageUrls, balance);
    }

    /**
     * Извлечь base64 изображения из ответа LaoZhang API (формат Gemini API).
     *
     * @param response ответ от LaoZhang API
     * @return список base64 строк в формате data:image/...;base64,...
     */
    private Mono<List<String>> extractBase64Images(LaoZhangResponseDTO response) {
        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
            logFullResponse(response, "Пустой ответ от LaoZhang API (response или candidates == null)");
            return Mono.error(new FalAIException(
                    ProviderConstants.ErrorMessages.EMPTY_RESPONSE,
                    HttpStatus.UNPROCESSABLE_ENTITY));
        }

        List<String> base64Images = getImagesFromResponse(response);

        if (base64Images.isEmpty()) {
            logFullResponse(response, "Не найдено base64 изображений в ответе LaoZhang API");
            return Mono.error(new FalAIException(
                    ProviderConstants.ErrorMessages.NO_IMAGES_IN_RESPONSE,
                    HttpStatus.UNPROCESSABLE_ENTITY));
        }

        return Mono.just(base64Images);
    }

    /**
     * Логировать полный ответ от провайдера в случае ошибки.
     * Включает полный JSON, даже если он содержит большие base64 данные.
     */
    private void logFullResponse(LaoZhangResponseDTO response, String reason) {
        try {
            if (response == null) {
                log.error("{} Response == null", reason);
                return;
            }

            String responseJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(response);
            log.error("{} Полный ответ от LaoZhang API (JSON, {} символов):\n{}",
                    reason, responseJson.length(), responseJson);
        } catch (Exception e) {
            log.error("{} Не удалось сериализовать ответ для логирования: {}. Response toString: {}",
                    reason, e.getMessage(), response);
        }
    }

    private List<String> getImagesFromResponse(LaoZhangResponseDTO response) {
        List<String> base64Images = new ArrayList<>();

        for (LaoZhangResponseDTO.Candidate candidate : response.getCandidates()) {
            if (candidate.getContent() != null && candidate.getContent().getParts() != null) {
                for (LaoZhangResponseDTO.Part part : candidate.getContent().getParts()) {
                    if (part.getInlineData() != null) {
                        String dataUrl = buildDataUrl(part.getInlineData().getMimeType(),
                                part.getInlineData().getData());
                        base64Images.add(dataUrl);
                    }
                }
            }
        }
        return base64Images;
    }

    /**
     * Построить data URL из mime type и base64 данных.
     */
    private String buildDataUrl(String mimeType, String base64Data) {
        return ProviderConstants.LaoZhang.DATA_URL_PREFIX
                + mimeType
                + ProviderConstants.LaoZhang.DATA_URL_SEPARATOR
                + base64Data;
    }

    /**
     * Создать версию запроса для логирования, где большие base64 данные заменены на информацию о размере.
     *
     * @param request оригинальный запрос
     * @return запрос для логирования с сокращенными данными изображений
     */
    private LaoZhangRequestDTO createLoggableRequest(LaoZhangRequestDTO request) {
        if (request == null || request.getContents() == null) {
            return request;
        }

        List<LaoZhangRequestDTO.Content> loggableContents = new ArrayList<>();
        for (LaoZhangRequestDTO.Content content : request.getContents()) {
            if (content == null || content.getParts() == null) {
                loggableContents.add(content);
                continue;
            }

            List<LaoZhangRequestDTO.Part> loggableParts = new ArrayList<>();
            for (LaoZhangRequestDTO.Part part : content.getParts()) {
                if (part == null) {
                    loggableParts.add(part);
                    continue;
                }

                // Если это изображение с inlineData, заменяем base64 на информацию о размере
                if (part.getInlineData() != null && part.getInlineData().getData() != null) {
                    String originalData = part.getInlineData().getData();
                    int dataSize = originalData.length();
                    String mimeType = part.getInlineData().getMimeType() != null 
                            ? part.getInlineData().getMimeType() 
                            : "unknown";
                    
                    // Приблизительный размер в байтах (base64 примерно на 33% больше оригинала)
                    int approximateBytes = (int) (dataSize * 0.75);
                    String sizeInfo = String.format("[base64 data: %d chars (~%d KB), mimeType: %s]", 
                            dataSize, approximateBytes / 1024, mimeType);
                    
                    LaoZhangRequestDTO.InlineData loggableInlineData = LaoZhangRequestDTO.InlineData.builder()
                            .mimeType(mimeType)
                            .data(sizeInfo)
                            .build();
                    
                    LaoZhangRequestDTO.Part loggablePart = LaoZhangRequestDTO.Part.builder()
                            .text(part.getText())
                            .inlineData(loggableInlineData)
                            .build();
                    
                    loggableParts.add(loggablePart);
                } else {
                    // Для текстовых частей оставляем как есть
                    loggableParts.add(part);
                }
            }

            LaoZhangRequestDTO.Content loggableContent = LaoZhangRequestDTO.Content.builder()
                    .parts(loggableParts)
                    .build();
            loggableContents.add(loggableContent);
        }

        return LaoZhangRequestDTO.builder()
                .contents(loggableContents)
                .generationConfig(request.getGenerationConfig())
                .build();
    }

    /**
     * Вычислить необходимое количество поинтов для генерации.
     */
    private Integer calculatePointsNeeded(ImageRq request) {
        return generationProperties.getPointsNeeded(
                request.getModel(),
                request.getResolution(),
                request.getNumImages()
        );
    }

    /**
     * Инициализация WebClient для LaoZhang API.
     * Настроен с увеличенными таймаутами для поддержки генерации 4K (до 12 минут).
     */
    private WebClient getWebClient() {
        if (webClient == null) {
            String baseUrl = laoZhangProperties.getApi().getUrl();
            String apiKey = laoZhangProperties.getApi().getKey();

            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("LaoZhang API ключ не настроен");
            }

            HttpClient httpClient = HttpClient.create()
                    .responseTimeout(ProviderConstants.LaoZhang.REQUEST_TIMEOUT)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                            ProviderConstants.LaoZhang.CONNECT_TIMEOUT_MS);

            webClient = webClientBuilder
                    .baseUrl(baseUrl)
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .build();
        }
        return webClient;
    }

    @Override
    public GenerationProvider getProviderName() {
        return GenerationProvider.LAOZHANG_AI;
    }

    @Override
    public Mono<Boolean> isAvailable() {
        String apiKey = laoZhangProperties.getApi().getKey();
        return Mono.just(apiKey != null && !apiKey.isEmpty());
    }

    @Override
    public Integer getPricePerImage(GenerationModelType modelType, Resolution resolution) {
        return generationProperties.getPointsNeeded(modelType, resolution, 1);
    }

    /**
     * Контекст генерации изображения.
     * Содержит все необходимые данные для выполнения генерации.
     */
    private static class GenerationContext {
        final ImageRq request;
        final Long userId;
        Integer pointsNeeded;
        boolean pointsDeducted = false; // Флаг, указывающий, были ли списаны поинты
        Session session;
        ArtStyle style;
        User user;
        String finalPrompt;
        UserPoints userPoints;
        List<String> base64Images;
        List<String> imageUrls;

        GenerationContext(ImageRq request, Long userId) {
            this.request = request;
            this.userId = userId;
        }
    }
}
