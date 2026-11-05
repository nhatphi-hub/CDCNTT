package com.smart_ecommerce.backend.dto.analytics;

import java.math.BigDecimal;

public record AnalyticsOverviewResponse(
        long totalOrders,
        BigDecimal totalRevenue,
        long totalItemsSold,
        long totalProductViews,
        long totalSearches,
        long totalReviews
) {
}
