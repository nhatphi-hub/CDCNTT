package com.smart_ecommerce.backend.dto.recommendation;

import com.smart_ecommerce.backend.entity.RecommendationEventType;
import com.smart_ecommerce.backend.entity.RecommendationType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateRecommendationEventRequest(
        @NotNull RecommendationType recommendationType,
        @NotNull UUID productId,
        @NotNull RecommendationEventType eventType,
        @DecimalMin(value = "0.0", inclusive = true)
        @DecimalMax(value = "1.0", inclusive = true)
        BigDecimal recommendationScore,
        @Min(1) Integer position,
        UUID sessionId,
        UUID clientEventId
) {
}
