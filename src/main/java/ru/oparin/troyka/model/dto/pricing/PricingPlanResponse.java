package ru.oparin.troyka.model.dto.pricing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingPlanResponse {

    private String id;
    private String name;
    private String description;
    private Integer credits;
    private Integer priceRub; // цена в копейках
    private Integer unitPriceRubComputed; // цена за генерацию в копейках
    private Boolean isActive;
    private Boolean isPopular;
    private Integer sortOrder;
}
