package com.smart_ecommerce.backend.dto.analytics;

import com.smart_ecommerce.backend.entity.RecommendationType;

import java.math.BigDecimal;

public record RecommendationTypeAnalyticsResponse(
        RecommendationType recommendationType,
        long impressions,
        long clicks,
        long views,
        BigDecimal ctr,
        long addToCarts,
        long conversions,
        BigDecimal conversionRate
) {
}
