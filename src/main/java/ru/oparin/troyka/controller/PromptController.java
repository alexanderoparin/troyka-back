package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.model.dto.prompt.EnhancePromptRequest;
import ru.oparin.troyka.model.dto.prompt.EnhancePromptResponse;
import ru.oparin.troyka.service.ArtStyleService;
import ru.oparin.troyka.service.PromptEnhancementService;

/**
 * Контроллер для работы с промптами.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/prompt")
@Tag(name = "Промпты", description = "API для работы с промптами")
public class PromptController {

    private final PromptEnhancementService promptEnhancementService;
    private final ArtStyleService artStyleService;

    @Operation(summary = "Улучшить промпт",
            description = "Улучшает промпт пользователя с учетом выбранного стиля и опциональных изображений через DeepInfra API")
    @PostMapping("/enhance")
    public Mono<ResponseEntity<EnhancePromptResponse>> enhancePrompt(@Valid @RequestBody EnhancePromptRequest request) {
        Long styleId = request.getStyleId() != null ? request.getStyleId() : artStyleService.getDefaultStyleId();
        
        return artStyleService.getStyleById(styleId)
                .flatMap(style -> promptEnhancementService.enhancePrompt(
                        request.getPrompt(),
                        request.getImageUrls(),
                        style
                ))
                .map(enhancedPrompt -> ResponseEntity.ok(EnhancePromptResponse.builder()
                        .enhancedPrompt(enhancedPrompt)
                        .build()));
    }
}

