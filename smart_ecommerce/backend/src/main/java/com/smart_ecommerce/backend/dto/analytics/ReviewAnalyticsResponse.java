package com.smart_ecommerce.backend.dto.analytics;

public record ReviewAnalyticsResponse(
        long totalReviews,
        double averageRating,
        long rating1Count,
        long rating2Count,
        long rating3Count,
        long rating4Count,
        long rating5Count
) {
}
