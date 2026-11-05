package com.smart_ecommerce.backend.dto.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductResponse(

        UUID id,

        UUID categoryId,

        String categoryName,

        String name,

        String slug,

        String sku,

        String brand,

        String description,

        BigDecimal price,

        BigDecimal salePrice,

        Integer quantity,

        String gender,

        String productType,

        String status,

        LocalDateTime createdAt,

        LocalDateTime updatedAt,

        String imageUrl
) {
    // Keeps legacy seller response mapping and callers source compatible.
    public ProductResponse(UUID id, UUID categoryId, String categoryName,
                           String name, String slug, String sku, String brand,
                           String description, BigDecimal price, BigDecimal salePrice,
                           Integer quantity, String gender, String productType,
                           String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, categoryId, categoryName, name, slug, sku, brand, description,
                price, salePrice, quantity, gender, productType, status,
                createdAt, updatedAt, null);
    }
}