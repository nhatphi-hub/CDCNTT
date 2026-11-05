package com.smart_ecommerce.backend.dto.productvariant;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductVariantResponse(

        UUID id,

        UUID productId,

        String productName,

        String size,

        String color,

        String sku,

        BigDecimal price,

        BigDecimal salePrice,

        Integer quantity,

        String status,

        LocalDateTime createdAt,

        LocalDateTime updatedAt,
        UUID imageId
) {
    public ProductVariantResponse(UUID id, UUID productId, String productName, String size,
                                  String color, String sku, BigDecimal price, BigDecimal salePrice,
                                  Integer quantity, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, productId, productName, size, color, sku, price, salePrice,
             quantity, status, createdAt, updatedAt, null);
    }
}