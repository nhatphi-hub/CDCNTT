package com.smart_ecommerce.backend.dto.analytics;

public record SearchAnalyticsResponse(
        String keyword,
        long searchCount
) {
}
