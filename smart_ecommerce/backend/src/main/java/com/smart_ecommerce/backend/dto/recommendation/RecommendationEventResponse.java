package com.smart_ecommerce.backend.dto.recommendation;

import com.smart_ecommerce.backend.entity.RecommendationEventType;
import com.smart_ecommerce.backend.entity.RecommendationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecommendationEventResponse(
        UUID eventId,
        UUID productId,
        RecommendationType recommendationType,
        RecommendationEventType eventType,
        LocalDateTime createdAt,
        boolean duplicate
) {
}
