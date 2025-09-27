package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
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
@RestController
@RequestMapping("/fal")
public class FalAIController {

    private final FalAIService falAIService;
    private final UserService userService;
    private final UserPointsService userPointsService;

    public FalAIController(FalAIService falAIService, UserService userService, UserPointsService userPointsService) {
        this.falAIService = falAIService;
        this.userService = userService;
        this.userPointsService = userPointsService;
    }

    @Operation(summary = "Синхронное создание изображения по описанию",
            description = "Генерация изображения на основе промпта, если заполнено поле imageUrls, то это редактирование переданных изображений")
    @PostMapping("/image/run/create")
    public Mono<ResponseEntity<ImageRs>> generateImage(@RequestBody ImageRq rq) {
        return SecurityUtil.getCurrentUsername()
                .doOnNext(username -> log.info("Получен запрос на создание изображения от пользователя с логином: {}", username))
                .flatMap(userService::findByUsername)
                .flatMap(user -> falAIService.getImageResponse(rq, user.getId()))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка при генерации изображения", e);
                    return Mono.just(ResponseEntity.badRequest().body(null));
                });
    }

    @Operation(summary = "Получение баланса пользователя",
            description = "Возвращает количество доступных баллов текущего пользователя")
    @GetMapping("/user/points")
    public Mono<ResponseEntity<Integer>> getUserPoints() {
        Mono<String> currentUsername = SecurityUtil.getCurrentUsername();
        return currentUsername
                .flatMap(userService::findByUsername)
                .flatMap(user -> userPointsService.getUserPoints(user.getId()))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Ошибка при получении баллов пользователя", e);
                    return Mono.just(ResponseEntity.badRequest().body(0));
                });
    }
}