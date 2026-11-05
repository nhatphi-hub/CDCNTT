package com.smart_ecommerce.backend.dto.productimage;

import java.util.UUID;

public record ProductImageResponse(
        UUID id,
        UUID productId,
        String productName,
        String imageUrl,
        Boolean isPrimary,
        Integer sortOrder
) {}
