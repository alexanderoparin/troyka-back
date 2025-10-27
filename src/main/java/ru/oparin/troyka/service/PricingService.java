package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import ru.oparin.troyka.model.dto.pricing.PricingPlanResponse;
import ru.oparin.troyka.model.entity.PricingPlan;
import ru.oparin.troyka.repository.PricingPlanRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingPlanRepository pricingPlanRepository;

    public Flux<PricingPlanResponse> getActivePricingPlans() {
        return pricingPlanRepository.findByIsActiveTrueOrderBySortOrderAsc()
                .map(this::mapToResponse)
                .doOnNext(plan -> log.debug("Retrieved pricing plan: {}", plan.getName()));
    }

    public Flux<PricingPlanResponse> getAllPricingPlans() {
        return pricingPlanRepository.findAllByOrderBySortOrderAsc()
                .map(this::mapToResponse)
                .doOnNext(plan -> log.debug("Retrieved pricing plan: {}", plan.getName()));
    }

    private PricingPlanResponse mapToResponse(PricingPlan entity) {
        // Рассчитываем цену за поинт (цена в копейках / количество поинтов)
        Integer unitPrice = entity.getCredits() != null && entity.getCredits() > 0 
                ? entity.getPriceRub() / entity.getCredits() 
                : null;
        
        return PricingPlanResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .credits(entity.getCredits())
                .priceRub(entity.getPriceRub())
                .unitPriceRubComputed(unitPrice)
                .isActive(entity.getIsActive())
                .isPopular(entity.getIsPopular())
                .sortOrder(entity.getSortOrder())
                .build();
    }
}
