package ru.oparin.troyka.service.provider;

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

    private WebClient webClient;

    @Override
    public Mono<ImageRs> generateImage(ImageRq request, Long userId) {
        log.debug("LaoZhangProvider: генерация изображения для пользователя {}", userId);

        GenerationContext context = new GenerationContext(request, userId);
        context.pointsNeeded = calculatePointsNeeded(request);

        return validatePoints(context)
                .flatMap(ignored -> prepareContext(context))
                .flatMap(this::deductPoints)
                .flatMap(this::executeGeneration)
                .flatMap(this::saveImages)
                .flatMap(this::saveHistory)
                .flatMap(this::updateSession)
                .map(this::createResponse)
                .doOnSuccess(response -> log.info("Успешно получен ответ с изображением для сессии {}: {}",
                        context.session.getId(), response))
                .onErrorResume(error -> errorHandler.handleError(userId, error, context.pointsNeeded));
    }

    /**
     * Валидировать наличие достаточного количества поинтов у пользователя.
     */
    private Mono<Void> validatePoints(GenerationContext context) {
        return userPointsService.hasEnoughPoints(context.userId, context.pointsNeeded)
                .flatMap(hasEnough -> {
                    if (!hasEnough) {
                        String message = String.format(
                                ProviderConstants.ErrorMessages.INSUFFICIENT_POINTS,
                                context.pointsNeeded
                        );
                        return Mono.error(new FalAIException(message, HttpStatus.PAYMENT_REQUIRED));
                    }
                    return Mono.empty();
                });
    }

    /**
     * Подготовить контекст генерации: получить сессию, стиль и пользователя.
     */
    private Mono<GenerationContext> prepareContext(GenerationContext context) {
        return sessionService.getOrCreateSession(context.request.getSessionId(), context.userId)
                .flatMap(session -> {
                    context.session = session;
                    return artStyleService.getStyleById(context.request.getStyleId());
                })
                .flatMap(style -> {
                    context.style = style;
                    return userService.findByIdOrThrow(context.userId);
                })
                .map(user -> {
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
                .flatMap(laoZhangRequest -> sendApiRequest(endpoint, laoZhangRequest, context))
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
                .timeout(ProviderConstants.LaoZhang.REQUEST_TIMEOUT);

        return apiRequest.doOnCancel(() ->
                errorHandler.handleCancellation(context.userId, context.pointsNeeded)
        );
    }

    /**
     * Сохранить изображения и получить их URL.
     */
    private Mono<GenerationContext> saveImages(GenerationContext context) {
        String username = context.user.getUsername();
        return Flux.fromIterable(context.base64Images)
                .flatMap(base64Image -> base64ImageService.saveBase64ImageAndGetUrl(
                        base64Image, username, ProviderConstants.LaoZhang.IMAGE_SUBDIRECTORY))
                .collectList()
                .map(imageUrls -> {
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
        Integer balance = context.userPoints.getPoints();
        return new ImageRs(context.imageUrls, balance);
    }

    /**
     * Извлечь base64 изображения из ответа LaoZhang API (формат Gemini API).
     *
     * @param response ответ от LaoZhang API
     * @return список base64 строк в формате data:image/...;base64,...
     */
    private Mono<List<String>> extractBase64Images(LaoZhangResponseDTO response) {
        log.debug("Извлечение base64 изображений из ответа LaoZhang API");

        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
            return Mono.error(new FalAIException(
                    ProviderConstants.ErrorMessages.EMPTY_RESPONSE,
                    HttpStatus.UNPROCESSABLE_ENTITY));
        }

        List<String> base64Images = getImagesFromResponse(response);

        if (base64Images.isEmpty()) {
            log.warn("Не найдено base64 изображений в ответе LaoZhang API");
            return Mono.error(new FalAIException(
                    ProviderConstants.ErrorMessages.NO_IMAGES_IN_RESPONSE,
                    HttpStatus.UNPROCESSABLE_ENTITY));
        }

        log.info("Извлечено {} base64 изображений из ответа LaoZhang API", base64Images.size());
        return Mono.just(base64Images);
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
