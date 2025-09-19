package ru.oparin.troyka.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.service.FalAIService;

import java.util.Map;

@RestController
@RequestMapping("/api/fal")
public class FalAIController {

    private final FalAIService falAIService;

    public FalAIController(FalAIService falAIService) {
        this.falAIService = falAIService;
    }

    @PostMapping("/generate")
    public Mono<ResponseEntity<String>> generateText(@RequestParam String prompt) {
        return falAIService.getTextResponse(prompt)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body("Error: " + e.getMessage())));
    }

    @GetMapping("/status/{requestId}")
    public Mono<ResponseEntity<Map<String, Object>>> getStatus(@PathVariable String requestId) {
        return falAIService.getRequestStatus(requestId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(Map.of("error", e.getMessage()))));
    }
}