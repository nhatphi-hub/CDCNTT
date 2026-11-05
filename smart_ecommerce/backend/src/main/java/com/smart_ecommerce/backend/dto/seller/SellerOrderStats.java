package com.smart_ecommerce.backend.dto.seller;
import java.math.BigDecimal;
import java.util.Map;
public record SellerOrderStats(long totalOrders, long unitsSold, Map<String, Long> statusCounts,
    BigDecimal paidDeliveredRevenue) {}
