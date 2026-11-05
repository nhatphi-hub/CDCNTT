package com.smart_ecommerce.backend.dto.recommendation;

import java.math.BigDecimal;
import java.util.UUID;

/** Minimal catalog fields required to render a recommendation. */
public record RecommendationResponse(
        UUID productId,
        String productName,
        String slug,
        String imageUrl,
        BigDecimal price,
        BigDecimal salePrice,
        BigDecimal score,
        String reason
) {
}
