package ru.oparin.troyka.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import ru.oparin.troyka.config.properties.GenerationProperties;
import ru.oparin.troyka.model.dto.pricing.PricingPlanResponse;
import ru.oparin.troyka.model.entity.PricingPlan;
import ru.oparin.troyka.repository.PricingPlanRepository;

/**
 * Сервис для работы с тарифными планами.
 * Предоставляет методы для получения активных и всех доступных тарифных планов,
 * включая расчет стоимости генераций.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingPlanRepository pricingPlanRepository;
    private final GenerationProperties generationProperties;

    /**
     * Получает список всех активных тарифных планов.
     * Планы возвращаются отсортированными по полю sortOrder в порядке возрастания.
     *
     * @return Flux с активными тарифными планами
     */
    public Flux<PricingPlanResponse> getActivePricingPlans() {
        return pricingPlanRepository.findByIsActiveTrueOrderBySortOrderAsc()
                .map(this::mapToResponse);
    }

    /**
     * Преобразует сущность PricingPlan в DTO PricingPlanResponse.
     * Автоматически рассчитывает стоимость одной генерации на основе
     * общего количества поинтов и цены плана.
     *
     * @param entity сущность тарифного плана
     * @return DTO с данными тарифного плана и рассчитанной стоимостью генерации
     */
    private PricingPlanResponse mapToResponse(PricingPlan entity) {
        // Рассчитываем цену за одну генерацию (цена в копейках / количество поинтов * поинты за генерацию)
        Integer unitPrice = null;
        if (entity.getCredits() != null && entity.getCredits() > 0 && generationProperties.getPointsPerImage() > 0) {
            int generationsCount = entity.getCredits() / generationProperties.getPointsPerImage();
            if (generationsCount > 0) {
                unitPrice = entity.getPriceRub() / generationsCount;
            }
        }
        
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
