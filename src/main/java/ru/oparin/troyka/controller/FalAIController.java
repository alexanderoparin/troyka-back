package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.dto.fal.ImageRs;
import ru.oparin.troyka.service.FalAIService;
import ru.oparin.troyka.service.UserPointsService;
import ru.oparin.troyka.service.UserService;
import ru.oparin.troyka.util.SecurityUtil;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/fal")
public class FalAIController {

    private final FalAIService falAIService;
    private final UserPointsService userPointsService;
    private final UserService userService;

    @Operation(summary = "Синхронное создание изображения по описанию",
            description = "Генерация изображения на основе промпта, если заполнено поле imageUrls, то это редактирование переданных изображений")
    @PostMapping("/image/run/create")
    public Mono<ResponseEntity<ImageRs>> generateImage(@RequestBody ImageRq rq) {
        return SecurityUtil.getCurrentUserId(userService)
                .doOnNext(userId -> log.info("Получен запрос на создание изображения от пользователя с ID: {}", userId))
                .flatMap(userId -> falAIService.getImageResponse(rq, userId))
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "Получение баланса пользователя",
            description = "Возвращает количество доступных поинтов текущего пользователя")
    @GetMapping("/user/points")
    public Mono<ResponseEntity<Integer>> getUserPoints() {
        return SecurityUtil.getCurrentUserId(userService)
                .flatMap(userPointsService::getUserPoints)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка при получении поинтов пользователя", e);
                    return Mono.just(ResponseEntity.badRequest().body(0));
                });
    }
}