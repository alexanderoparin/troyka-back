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
import ru.oparin.troyka.service.FalAIQueueService;
import ru.oparin.troyka.service.UserService;
import ru.oparin.troyka.util.SecurityUtil;

import java.util.List;

/**
 * Контроллер для работы с очередями генерации изображений через Fal.ai.
 */
@Slf4j
@RestController
@RequestMapping("/generate")
@RequiredArgsConstructor
@Tag(name = "Generate", description = "API для генерации изображений через очередь Fal.ai")
public class GenerateController {

    private final FalAIQueueService falAIQueueService;
    private final UserService userService;

    /**
     * Отправить запрос на генерацию изображения в очередь Fal.ai.
     *
     * @param imageRq запрос на генерацию изображения
     * @return запись истории генерации с falRequestId
     */
    @Operation(summary = "Отправить запрос на генерацию в очередь",
            description = "Отправляет запрос в очередь Fal.ai и возвращает идентификатор для отслеживания статуса")
    @PostMapping("/submit")
    public Mono<ResponseEntity<FalAIQueueRequestStatusDTO>> submitToQueue(@RequestBody ImageRq imageRq) {
        return SecurityUtil.checkStudioAccess(userService)
                .flatMap(userId -> falAIQueueService.submitToQueue(imageRq, userId))
                .map(ResponseEntity::ok)
                .doOnError(error -> {
                    if (error instanceof FalAIException falEx) {
                        // Ожидаемые бизнес-ошибки логируем без стектрейса
                        log.warn("Ошибка при отправке запроса в очередь: {}", falEx.getMessage());
                    } else {
                        // Неожиданные ошибки логируем с полным стектрейсом
                        log.error("Ошибка при отправке запроса в очередь", error);
                    }
                });
    }

    /**
     * Получить статус запроса генерации по внутреннему ID.
     *
     * @param id внутренний идентификатор записи в ImageGenerationHistory
     * @return статус запроса
     */
    @Operation(summary = "Получить статус запроса генерации",
            description = "Возвращает текущий статус запроса генерации по внутреннему ID")
    @GetMapping("/status/{id}")
    public Mono<ResponseEntity<FalAIQueueRequestStatusDTO>> getRequestStatus(@PathVariable Long id) {
        return SecurityUtil.checkStudioAccess(userService)
                .flatMap(userId -> falAIQueueService.getRequestStatus(id, userId))
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
}