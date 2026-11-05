package com.smart_ecommerce.backend.dto.cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartResponse(

        UUID id,

        UUID userId,

        List<CartItemResponse> items,

        Integer totalQuantity,

        BigDecimal totalAmount
) {
}