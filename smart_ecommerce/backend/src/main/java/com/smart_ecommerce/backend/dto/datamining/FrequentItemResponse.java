package com.smart_ecommerce.backend.dto.datamining;

import java.math.BigDecimal;
import java.util.UUID;

/** A frequent two-product itemset, counted once per order. */
public record FrequentItemResponse(
        UUID productId1,
        String productName1,
        UUID productId2,
        String productName2,
        long transactionCount,
        BigDecimal support
) {
}
