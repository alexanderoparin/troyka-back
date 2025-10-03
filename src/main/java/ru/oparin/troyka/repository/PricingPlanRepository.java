package ru.oparin.troyka.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import ru.oparin.troyka.model.entity.PricingPlan;

public interface PricingPlanRepository extends ReactiveCrudRepository<PricingPlan, String> {

    Flux<PricingPlan> findByIsActiveTrueOrderBySortOrderAsc();

    Flux<PricingPlan> findAllByOrderBySortOrderAsc();
}
