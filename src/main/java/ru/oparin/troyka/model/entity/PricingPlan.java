package ru.oparin.troyka.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "pricing_plans", schema = "troyka")
public class PricingPlan {

    @Id
    private String id;

    private String name;

    private String description;

    private Integer credits;

    private Integer priceRub; // цена в копейках

    private Integer unitPriceRubComputed; // цена за поинт в копейках

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Boolean isPopular = false;

    @Builder.Default
    private Integer sortOrder = 0;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
