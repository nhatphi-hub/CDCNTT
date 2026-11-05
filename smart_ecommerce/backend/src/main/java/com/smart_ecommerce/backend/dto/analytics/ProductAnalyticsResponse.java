package com.smart_ecommerce.backend.dto.analytics;

import java.util.UUID;

public record ProductAnalyticsResponse(
        UUID productId,
        String productName,
        long viewCount,
        long orderCount,
        long quantitySold,
        long reviewCount,
        double averageRating
) {
}
