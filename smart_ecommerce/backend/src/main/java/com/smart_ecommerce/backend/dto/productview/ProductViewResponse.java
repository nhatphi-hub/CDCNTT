package com.smart_ecommerce.backend.dto.productview;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProductViewResponse(
        UUID id,
        UUID userId,
        UUID productId,
        String productName,
        String productSlug,
        LocalDateTime viewedAt,
        Integer durationSeconds
) {}
