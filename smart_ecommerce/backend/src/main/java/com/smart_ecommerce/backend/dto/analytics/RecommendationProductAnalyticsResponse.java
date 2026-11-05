package com.smart_ecommerce.backend.dto.analytics;

import java.math.BigDecimal;
import java.util.UUID;

public record RecommendationProductAnalyticsResponse(
        UUID productId,
        String productName,
        long impressions,
        long clicks,
        long views,
        BigDecimal ctr,
        long addToCarts,
        long conversions,
        BigDecimal conversionRate
) {
}
