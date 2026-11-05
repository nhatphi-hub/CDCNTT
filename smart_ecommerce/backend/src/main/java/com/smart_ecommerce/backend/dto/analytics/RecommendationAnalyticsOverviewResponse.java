package com.smart_ecommerce.backend.dto.analytics;

import java.math.BigDecimal;

public record RecommendationAnalyticsOverviewResponse(
        long totalImpressions,
        long totalClicks,
        long totalViews,
        BigDecimal ctr,
        long totalAddToCarts,
        long totalConversions,
        BigDecimal conversionRate
) {
}
