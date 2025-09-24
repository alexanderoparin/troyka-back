package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

    @Operation(
            summary = "Создание изображения по описанию",
            description = "Генерация изображения на основе промпта",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешная генерация",
                            content = @Content(schema = @Schema(implementation = ImageRs.class))),
                    @ApiResponse(responseCode = "401", description = "Неверные учетные данные"),
                    @ApiResponse(responseCode = "404", description = "Пользователь не найден")
            }
    )
    @PostMapping("/image/new")
    public Mono<ResponseEntity<ImageRs>> generateImage(@RequestBody ImageRq rq) {
        return SecurityUtil.getCurrentUsername()
                .doOnNext(username -> log.info("Получен запрос на создание изображения от пользователя с логином: {}", username))
                .flatMap(username -> falAIService.getImageResponse(rq))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(null)));
    }

}