package ru.oparin.troyka.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.model.dto.pricing.GenerationPointsResponse;
import ru.oparin.troyka.model.dto.pricing.PricingPlanResponse;
import ru.oparin.troyka.service.PricingService;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/pricing")
@Tag(name = "Тарифы", description = "API для получения информации о тарифных планах")
public class PricingController {

    private final PricingService pricingService;
    private final GenerationProperties generationProperties;

    @Operation(summary = "Получить активные тарифные планы",
            description = "Возвращает список активных тарифных планов, отсортированных по порядку")
    @GetMapping("/plans")
    public Mono<ResponseEntity<List<PricingPlanResponse>>> getActivePricingPlans() {
        return pricingService.getActivePricingPlans()
                .collectList()
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "Получить стоимость генерации в поинтах",
            description = "Единственный источник тарифов в поинтах для фронта. Кэшируется на клиенте.")
    @GetMapping("/generation-points")
    public Mono<ResponseEntity<GenerationPointsResponse>> getGenerationPoints() {
        GenerationPointsResponse response = new GenerationPointsResponse(
                generationProperties.getPointsPerImage(),
                generationProperties.getPointsPerImageProForApi(),
                generationProperties.getPointsPerImageSeedream(),
                generationProperties.getPointsOnRegistration()
        );
        return Mono.just(ResponseEntity.ok(response));
    }
}
