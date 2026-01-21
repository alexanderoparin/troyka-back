package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.exception.FalAIException;
import ru.oparin.troyka.model.dto.fal.FalAIQueueRequestStatusDTO;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.enums.GenerationProvider;
import ru.oparin.troyka.service.FalAIQueueService;
import ru.oparin.troyka.service.ImageGenerationHistoryService;
import ru.oparin.troyka.service.UserService;
import ru.oparin.troyka.service.provider.GenerationProviderRouter;
import ru.oparin.troyka.util.SecurityUtil;

import java.util.List;

/**
 * Контроллер для работы с очередями генерации изображений.
 * Для FAL AI использует очередь, для других провайдеров - синхронную генерацию.
 */
@Slf4j
@RestController
@RequestMapping("/generate")
@RequiredArgsConstructor
@Tag(name = "Generate", description = "API для генерации изображений через очередь или синхронно")
public class GenerateController {

    private final FalAIQueueService falAIQueueService;
    private final GenerationProviderRouter providerRouter;
    private final ImageGenerationHistoryService imageGenerationHistoryService;
    private final UserService userService;

    /**
     * Отправить запрос на генерацию изображения в очередь Fal.ai.
     *
     * @param imageRq запрос на генерацию изображения
     * @return запись истории генерации с falRequestId
     */
    @Operation(summary = "Отправить запрос на генерацию в очередь",
            description = "Отправляет запрос в очередь Fal.ai (если активен FAL AI) или выполняет синхронную генерацию (для других провайдеров)")
    @PostMapping("/submit")
    public Mono<ResponseEntity<FalAIQueueRequestStatusDTO>> submitToQueue(@RequestBody ImageRq imageRq) {
        return SecurityUtil.checkStudioAccess(userService)
                .flatMap(userId -> providerRouter.getActiveProvider()
                        .flatMap(activeProvider -> {
                            if (activeProvider == GenerationProvider.FAL_AI) {
                                // Для FAL AI используем очередь
                                log.debug("Использование очереди FAL AI для пользователя {}", userId);
                                return falAIQueueService.submitToQueue(imageRq, userId);
                            } else {
                                // Для других провайдеров (например, LaoZhang AI) используем синхронную генерацию
                                log.debug("Использование синхронной генерации через провайдер {} для пользователя {}", activeProvider, userId);
                                return providerRouter.generateImage(imageRq, userId)
                                        .flatMap(imageRs -> {
                                            // Находим последнюю сохраненную запись истории для получения реального ID
                                            return imageGenerationHistoryService.getLastHistoryBySessionId(imageRq.getSessionId(), userId)
                                                    .map(history -> {
                                                        // Преобразуем ImageRs в FalAIQueueRequestStatusDTO для совместимости
                                                        FalAIQueueRequestStatusDTO dto = new FalAIQueueRequestStatusDTO();
                                                        dto.setId(history.getId());
                                                        dto.setQueueStatus(ru.oparin.troyka.model.enums.QueueStatus.COMPLETED);
                                                        dto.setQueuePosition(0);
                                                        dto.setImageUrls(imageRs.getImageUrls());
                                                        dto.setPrompt(imageRq.getPrompt());
                                                        dto.setSessionId(imageRq.getSessionId());
                                                        return dto;
                                                    })
                                                    .switchIfEmpty(Mono.defer(() -> {
                                                        // Если история не найдена (не должно происходить), возвращаем с ID=0
                                                        log.warn("История не найдена для синхронной генерации, userId={}, sessionId={}", userId, imageRq.getSessionId());
                                                        FalAIQueueRequestStatusDTO dto = new FalAIQueueRequestStatusDTO();
                                                        dto.setId(0L);
                                                        dto.setQueueStatus(ru.oparin.troyka.model.enums.QueueStatus.COMPLETED);
                                                        dto.setQueuePosition(0);
                                                        dto.setImageUrls(imageRs.getImageUrls());
                                                        dto.setPrompt(imageRq.getPrompt());
                                                        dto.setSessionId(imageRq.getSessionId());
                                                        return Mono.just(dto);
                                                    }));
                                        });
                            }
                        }))
                .map(ResponseEntity::ok)
                .doOnError(error -> {
                    if (error instanceof FalAIException falEx) {
                        // Ожидаемые бизнес-ошибки логируем без стектрейса
                        log.warn("Ошибка при отправке запроса на генерацию: {}", falEx.getMessage());
                    } else {
                        // Неожиданные ошибки логируем с полным стектрейсом
                        log.error("Ошибка при отправке запроса на генерацию", error);
                    }
                });
    }

    /**
     * Получить статус запроса генерации по внутреннему ID.
     *
     * @param idStr строковое представление внутреннего идентификатора записи в ImageGenerationHistory (будет валидировано и преобразовано в Long)
     * @return статус запроса
     * @throws IllegalArgumentException если idStr некорректный (null, пустой, "undefined", "null" или не число)
     */
    @Operation(summary = "Получить статус запроса генерации",
            description = "Возвращает текущий статус запроса генерации по внутреннему ID")
    @GetMapping("/status/{id}")
    public Mono<ResponseEntity<FalAIQueueRequestStatusDTO>> getRequestStatus(@PathVariable("id") String idStr) {
        Long id = validateAndParseId(idStr);
        
        return SecurityUtil.checkStudioAccess(userService)
                .flatMap(userId -> providerRouter.getActiveProvider()
                        .flatMap(activeProvider -> {
                            if (activeProvider == GenerationProvider.FAL_AI || id != 0) {
                                // Для FAL AI или если ID не 0, используем стандартный метод
                                return falAIQueueService.getRequestStatus(id, userId);
                            } else {
                                // Для синхронных запросов (ID=0) с не-FAL провайдером ищем последнюю запись истории
                                log.debug("Получение статуса для синхронной генерации, userId={}, id={}", userId, id);
                                return Mono.error(new FalAIException("Запрос не найден", org.springframework.http.HttpStatus.NOT_FOUND));
                            }
                        }))
                .map(ResponseEntity::ok)
                .doOnError(error -> {
                    if (error instanceof FalAIException falEx) {
                        log.warn("Ошибка при получении статуса запроса {}: {}", id, falEx.getMessage());
                    } else {
                        log.error("Ошибка при получении статуса запроса {}", id, error);
                    }
                });
    }

    /**
     * Получить все активные запросы текущего пользователя.
     *
     * @return список активных запросов
     */
    @Operation(summary = "Получить активные запросы пользователя",
            description = "Возвращает все активные запросы генерации (в очереди или обрабатываются) текущего пользователя")
    @GetMapping("/user/active")
    public Mono<ResponseEntity<List<FalAIQueueRequestStatusDTO>>> getUserActiveRequests() {
        return SecurityUtil.checkStudioAccess(userService)
                .flatMap(userId -> falAIQueueService.getUserActiveRequests(userId)
                        .collectList()
                        .map(ResponseEntity::ok))
                .doOnError(error -> {
                    if (error instanceof FalAIException falEx) {
                        log.warn("Ошибка при получении активных запросов пользователя: {}", falEx.getMessage());
                    } else {
                        log.error("Ошибка при получении активных запросов пользователя", error);
                    }
                });
    }

    /**
     * Валидировать и преобразовать строковый id в Long.
     * 
     * @param idStr строковое представление идентификатора
     * @return Long идентификатор
     * @throws IllegalArgumentException если id некорректный
     */
    private Long validateAndParseId(String idStr) {
        if (idStr == null || idStr.isEmpty() || 
            "undefined".equalsIgnoreCase(idStr) || 
            "null".equalsIgnoreCase(idStr)) {
            throw new IllegalArgumentException("Некорректный идентификатор: " + idStr);
        }
        
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректный формат идентификатора: " + idStr, e);
        }
    }
}