package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.fal.ImageRq;
import ru.oparin.troyka.model.dto.fal.ImageRs;
import ru.oparin.troyka.service.FalAIService;
import ru.oparin.troyka.util.SecurityUtil;

@Slf4j
@RestController
@RequestMapping("/fal")
public class FalAIController {

    private final FalAIService falAIService;

    public FalAIController(FalAIService falAIService) {
        this.falAIService = falAIService;
    }

    @Operation(summary = "Синхронное создание изображения по описанию",
            description = "Генерация изображения на основе промпта, если заполнено поле imageUrls, то это редактирование переданных изображений")
    @PostMapping("/image/run/create")
    public Mono<ResponseEntity<ImageRs>> generateImage(@RequestBody ImageRq rq) {
        return SecurityUtil.getCurrentUsername()
                .doOnNext(username -> log.info("Получен запрос на создание изображения от пользователя с логином: {}", username))
                .flatMap(username -> falAIService.getImageResponse(rq))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(null)));
    }
}