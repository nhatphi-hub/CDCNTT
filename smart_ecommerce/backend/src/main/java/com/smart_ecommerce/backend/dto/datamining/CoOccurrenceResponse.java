package com.smart_ecommerce.backend.dto.datamining;

import java.math.BigDecimal;
import java.util.UUID;

/** Products purchased with the requested product in eligible orders. */
public record CoOccurrenceResponse(
        UUID productId,
        String productName,
        long transactionCount,
        BigDecimal support,
        BigDecimal confidence
) {
}
