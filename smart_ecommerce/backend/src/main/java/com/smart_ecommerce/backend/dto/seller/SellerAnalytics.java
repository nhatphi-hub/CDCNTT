package com.smart_ecommerce.backend.dto.seller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sales recognized only for whole orders marked DELIVERED and PAID. */
public record SellerAnalytics(
    int days, LocalDate fromDate, LocalDate toDate,
    long totalOrders, long completedOrders, long unitsSold,
    BigDecimal paidDeliveredRevenue, Map<String, Long> statusCounts,
    List<DailyRevenue> dailyRevenue, List<TopProduct> topProducts
) {
    /** Date reflects order creation, not delivery, because no delivery timestamp exists. */
    public record DailyRevenue(LocalDate date, BigDecimal revenue, long completedOrders) {}
    public record TopProduct(UUID productId, String productName, long unitsSold, BigDecimal revenue) {}
}
