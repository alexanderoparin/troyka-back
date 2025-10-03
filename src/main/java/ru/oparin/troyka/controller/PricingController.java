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

    @Operation(summary = "Получить активные тарифные планы",
            description = "Возвращает список активных тарифных планов, отсортированных по порядку")
    @GetMapping("/plans")
    public Mono<ResponseEntity<List<PricingPlanResponse>>> getActivePricingPlans() {
        log.info("Получен запрос на получение активных тарифных планов");
        return pricingService.getActivePricingPlans()
                .collectList()
                .map(ResponseEntity::ok)
                .doOnSuccess(plans -> log.info("Возвращено {} активных тарифных планов", plans.getBody().size()));
    }

    @Operation(summary = "Получить все тарифные планы",
            description = "Возвращает список всех тарифных планов (включая неактивные)")
    @GetMapping("/plans/all")
    public Mono<ResponseEntity<List<PricingPlanResponse>>> getAllPricingPlans() {
        log.info("Получен запрос на получение всех тарифных планов");
        return pricingService.getAllPricingPlans()
                .collectList()
                .map(ResponseEntity::ok)
                .doOnSuccess(plans -> log.info("Возвращено {} тарифных планов", plans.getBody().size()));
    }
}
