package com.smart_ecommerce.backend.dto.analytics;

import java.math.BigDecimal;

public record SalesTrendResponse(
        String period,
        long orderCount,
        long quantitySold,
        BigDecimal revenue
) {
}
