package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.dto.analytics.ProductViewAnalyticsResponse;
import com.smart_ecommerce.backend.dto.analytics.SearchAnalyticsResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class AnalyticsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public long countOrders(LocalDateTime from, LocalDateTime toExclusive) {
        return entityManager.createQuery("""
                        select count(ord)
                        from Order ord
                        where ord.createdAt >= :from and ord.createdAt < :to
                        """, Long.class)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .getSingleResult();
    }

    public BigDecimal sumPaidOrderTotals(LocalDateTime from, LocalDateTime toExclusive) {
        BigDecimal total = entityManager.createQuery("""
                        select sum(ord.totalAmount)
                        from Order ord
                        where ord.createdAt >= :from and ord.createdAt < :to
                          and upper(ord.status) <> 'CANCELLED'
                          and upper(ord.paymentStatus) = 'PAID'
                        """, BigDecimal.class)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .getSingleResult();
        return total == null ? BigDecimal.ZERO : total;
    }

    public long sumPaidOrderItemQuantities(LocalDateTime from, LocalDateTime toExclusive) {
        Long total = entityManager.createQuery("""
                        select sum(item.quantity)
                        from OrderItem item
                        where item.order.createdAt >= :from and item.order.createdAt < :to
                          and upper(item.order.status) <> 'CANCELLED'
                          and upper(item.order.paymentStatus) = 'PAID'
                        """, Long.class)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .getSingleResult();
        return total == null ? 0 : total;
    }

    public long countProductViews(LocalDateTime from, LocalDateTime toExclusive) {
        return entityManager.createQuery("""
                        select count(view)
                        from ProductView view
                        where view.viewedAt >= :from and view.viewedAt < :to
                        """, Long.class)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .getSingleResult();
    }

    public long countSearches(LocalDateTime from, LocalDateTime toExclusive) {
        return entityManager.createQuery("""
                        select count(history)
                        from SearchHistory history
                        where history.searchedAt >= :from and history.searchedAt < :to
                        """, Long.class)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .getSingleResult();
    }

    public long countReviews(LocalDateTime from, LocalDateTime toExclusive) {
        return entityManager.createQuery("""
                        select count(review)
                        from Review review
                        where review.createdAt >= :from and review.createdAt < :to
                        """, Long.class)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .getSingleResult();
    }

    public Map<UUID, Long> productViewCounts(
            List<UUID> productIds,
            LocalDateTime from,
            LocalDateTime toExclusive
    ) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createQuery("""
                        select view.product.id, count(view)
                        from ProductView view
                        where view.viewedAt >= :from and view.viewedAt < :to
                          and view.product.id in :productIds
                        group by view.product.id
                        """, Object[].class)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .setParameter("productIds", productIds)
                .getResultList();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    public Map<UUID, ProductSalesAggregate> productSales(
            List<UUID> productIds,
            LocalDateTime from,
            LocalDateTime toExclusive
    ) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createQuery("""
                        select item.product.id, count(distinct item.order.id), sum(item.quantity)
                        from OrderItem item
                        where item.order.createdAt >= :from and item.order.createdAt < :to
                          and upper(item.order.status) <> 'CANCELLED'
                          and upper(item.order.paymentStatus) = 'PAID'
                          and item.product.id in :productIds
                        group by item.product.id
                        """, Object[].class)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .setParameter("productIds", productIds)
                .getResultList();
        Map<UUID, ProductSalesAggregate> aggregates = new HashMap<>();
        for (Object[] row : rows) {
            aggregates.put((UUID) row[0], new ProductSalesAggregate(
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue()));
        }
        return aggregates;
    }

    public Map<UUID, ProductReviewAggregate> productReviews(
            List<UUID> productIds,
            LocalDateTime from,
            LocalDateTime toExclusive
    ) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createQuery("""
                        select review.product.id, count(review), avg(review.rating)
                        from Review review
                        where review.createdAt >= :from and review.createdAt < :to
                          and review.product.id in :productIds
                        group by review.product.id
                        """, Object[].class)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .setParameter("productIds", productIds)
                .getResultList();
        Map<UUID, ProductReviewAggregate> aggregates = new HashMap<>();
        for (Object[] row : rows) {
            aggregates.put((UUID) row[0], new ProductReviewAggregate(
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).doubleValue()));
        }
        return aggregates;
    }

    public List<UUID> topProductIdsByViews(
            LocalDateTime from,
            LocalDateTime toExclusive,
            int limit
    ) {
        return entityManager.createQuery("""
                        select view.product.id
                        from ProductView view
                        where view.viewedAt >= :from and view.viewedAt < :to
                        group by view.product.id
                        order by count(view) desc, view.product.id asc
                        """, UUID.class)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<UUID> topProductIdsBySales(
            LocalDateTime from,
            LocalDateTime toExclusive,
            int limit,
            boolean sortByOrderCount
    ) {
        String orderBy = sortByOrderCount
                ? "count(distinct item.order.id) desc"
                : "sum(item.quantity) desc";
        TypedQuery<UUID> query = entityManager.createQuery("""
                        select item.product.id
                        from OrderItem item
                        where item.order.createdAt >= :from and item.order.createdAt < :to
                          and upper(item.order.status) <> 'CANCELLED'
                          and upper(item.order.paymentStatus) = 'PAID'
                        group by item.product.id
                        order by """ + orderBy + ", item.product.id asc", UUID.class);
        return query.setParameter("from", from)
                .setParameter("to", toExclusive)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<SearchAnalyticsResponse> topSearches(
            LocalDateTime from,
            LocalDateTime toExclusive,
            int limit
    ) {
        List<Object[]> rows = entityManager.createQuery("""
                        select lower(trim(history.keyword)), count(history)
                        from SearchHistory history
                        where history.searchedAt >= :from and history.searchedAt < :to
                        group by lower(trim(history.keyword))
                        order by count(history) desc, lower(trim(history.keyword)) asc
                        """, Object[].class)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .setMaxResults(limit)
                .getResultList();
        List<SearchAnalyticsResponse> results = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            results.add(new SearchAnalyticsResponse(
                    (String) row[0], ((Number) row[1]).longValue()));
        }
        return results;
    }

    public List<ProductViewAnalyticsResponse> productViews(
            LocalDateTime from,
            LocalDateTime toExclusive,
            int limit
    ) {
        return entityManager.createQuery("""
                        select new com.smart_ecommerce.backend.dto.analytics.ProductViewAnalyticsResponse(
                            view.product.id, view.product.name, count(view))
                        from ProductView view
                        where view.viewedAt >= :from and view.viewedAt < :to
                        group by view.product.id, view.product.name
                        order by count(view) desc, view.product.name asc
                        """, ProductViewAnalyticsResponse.class)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .setMaxResults(limit)
                .getResultList();
    }

    public ReviewAggregate reviewStatistics(LocalDateTime from, LocalDateTime toExclusive) {
        Object[] row = entityManager.createQuery("""
                        select count(review), avg(review.rating),
                               sum(case when review.rating = 1 then 1 else 0 end),
                               sum(case when review.rating = 2 then 1 else 0 end),
                               sum(case when review.rating = 3 then 1 else 0 end),
                               sum(case when review.rating = 4 then 1 else 0 end),
                               sum(case when review.rating = 5 then 1 else 0 end)
                        from Review review
                        where review.createdAt >= :from and review.createdAt < :to
                        """, Object[].class)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .getSingleResult();
        return new ReviewAggregate(
                numberAsLong(row[0]),
                numberAsDouble(row[1]),
                numberAsLong(row[2]),
                numberAsLong(row[3]),
                numberAsLong(row[4]),
                numberAsLong(row[5]),
                numberAsLong(row[6]));
    }

    public Map<LocalDateTime, SalesAggregate> salesByOrderDate(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String groupBy
    ) {
        String period = dateTruncExpression(groupBy, "ord.createdAt");
        String query = "select " + period + ", count(ord), sum(ord.totalAmount) "
                + "from Order ord "
                + "where ord.createdAt >= :from and ord.createdAt < :to "
                + "and upper(ord.status) <> 'CANCELLED' "
                + "and upper(ord.paymentStatus) = 'PAID' "
                + "group by " + period + " order by " + period;
        List<Object[]> rows = entityManager.createQuery(query, Object[].class)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .getResultList();

        Map<LocalDateTime, SalesAggregate> aggregates = new LinkedHashMap<>();
        for (Object[] row : rows) {
            aggregates.put(toLocalDateTime(row[0]), new SalesAggregate(
                    ((Number) row[1]).longValue(),
                    (BigDecimal) row[2]));
        }
        return aggregates;
    }

    public Map<LocalDateTime, Long> itemQuantitiesByOrderDate(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String groupBy
    ) {
        String period = dateTruncExpression(groupBy, "item.order.createdAt");
        String query = "select " + period + ", sum(item.quantity) "
                + "from OrderItem item "
                + "where item.order.createdAt >= :from and item.order.createdAt < :to "
                + "and upper(item.order.status) <> 'CANCELLED' "
                + "and upper(item.order.paymentStatus) = 'PAID' "
                + "group by " + period + " order by " + period;
        List<Object[]> rows = entityManager.createQuery(query, Object[].class)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .getResultList();

        Map<LocalDateTime, Long> quantities = new HashMap<>();
        for (Object[] row : rows) {
            quantities.put(toLocalDateTime(row[0]), ((Number) row[1]).longValue());
        }
        return quantities;
    }

    private static long numberAsLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static String dateTruncExpression(String groupBy, String timestampPath) {
        String unit = switch (groupBy) {
            case "day" -> "day";
            case "month" -> "month";
            default -> throw new IllegalArgumentException("Unsupported date_trunc unit");
        };
        return "function('date_trunc', '" + unit + "', " + timestampPath + ")";
    }

    private static double numberAsDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new IllegalStateException("Database returned an unsupported date_trunc value");
    }

    public record ProductSalesAggregate(long orderCount, long quantitySold) {
    }

    public record ProductReviewAggregate(long reviewCount, double averageRating) {
    }

    public record ReviewAggregate(
            long totalReviews,
            double averageRating,
            long rating1Count,
            long rating2Count,
            long rating3Count,
            long rating4Count,
            long rating5Count
    ) {
    }

    public record SalesAggregate(long orderCount, BigDecimal revenue) {
    }
}
