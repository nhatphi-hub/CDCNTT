package com.smart_ecommerce.backend.dto.cart;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(

        UUID id,

        UUID productId,

        String productName,

        String productSlug,

        String productImage,

        BigDecimal unitPrice,

        Integer quantity,

        BigDecimal subtotal,

        Integer availableQuantity,

        UUID variantId,
        String shopName,
        String variantName
) {
}