package com.smart_ecommerce.backend.dto.analytics;

import java.util.UUID;

public record ProductViewAnalyticsResponse(
        UUID productId,
        String productName,
        long viewCount
) {
}
