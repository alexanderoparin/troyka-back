package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.fal.ImageResponseDTO;
import ru.oparin.troyka.service.FalAIService;

@RestController
@RequestMapping("/api/fal")
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
                            content = @Content(schema = @Schema(implementation = ImageResponseDTO.class))),
                    @ApiResponse(responseCode = "401", description = "Неверные учетные данные"),
                    @ApiResponse(responseCode = "404", description = "Пользователь не найден")
            }
    )
    @PostMapping("/generate-image")
    public Mono<ResponseEntity<ImageResponseDTO>> generateImage(@RequestParam String prompt) {
        return falAIService.getImageResponse(prompt)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(null)));
    }
}