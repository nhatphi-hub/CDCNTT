package com.smart_ecommerce.backend.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        String orderCode,
        UUID userId,
        String paymentMethod,
        String provider,
        BigDecimal amount,
        String currency,
        String status,
        String providerTransactionId,
        String idempotencyKey,
        String failureCode,
        String failureMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime paidAt,
        LocalDateTime expiredAt
) {
}
