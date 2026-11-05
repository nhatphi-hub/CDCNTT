package com.smart_ecommerce.backend.dto.shipping;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ShippingResponse(
        UUID id,
        UUID orderId,
        String orderCode,
        String trackingCode,
        BigDecimal shippingFee,
        String shippingStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
