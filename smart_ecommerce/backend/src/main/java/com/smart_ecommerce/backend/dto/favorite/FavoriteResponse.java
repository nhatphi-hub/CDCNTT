package com.smart_ecommerce.backend.dto.favorite;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record FavoriteResponse(
        UUID id,
        UUID productId,
        String productName,
        String slug,
        String brand,
        BigDecimal price,
        BigDecimal salePrice,
        LocalDateTime createdAt
) {
}
