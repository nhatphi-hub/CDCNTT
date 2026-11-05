package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.RecommendationEvent;
import com.smart_ecommerce.backend.entity.RecommendationEventType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class RecommendationTrackingRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public RecommendationEvent findByClientEventId(
            UUID userId,
            UUID sessionId,
            UUID clientEventId
    ) {
        if (userId == null && sessionId == null) {
            return null;
        }
        String predicate = userId != null
                ? "event.user.id = :ownerId"
                : "event.sessionId = :ownerId";
        TypedQuery<RecommendationEvent> query = entityManager.createQuery(
                "select event from RecommendationEvent event where " + predicate
                        + " and event.clientEventId = :clientEventId",
                RecommendationEvent.class);
        query.setParameter("ownerId", userId != null ? userId : sessionId);
        query.setParameter("clientEventId", clientEventId);
        return query.getResultList().stream().findFirst().orElse(null);
    }

    public void save(RecommendationEvent event) {
        entityManager.persist(event);
    }

    public boolean existsPurchaseForOrderProduct(UUID orderId, UUID productId) {
        return entityManager.createQuery("""
                        select count(event)
                        from RecommendationEvent event
                        where event.order.id = :orderId
                          and event.product.id = :productId
                          and event.eventType = :eventType
                        """, Long.class)
                .setParameter("orderId", orderId)
                .setParameter("productId", productId)
                .setParameter("eventType", RecommendationEventType.PURCHASE)
                .getSingleResult() > 0;
    }

    public List<RecommendationEvent> findLatestClicksBefore(
            UUID userId,
            List<UUID> productIds,
            LocalDateTime orderCreatedAt
    ) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<RecommendationEvent> events = entityManager.createNativeQuery("""
                        SELECT DISTINCT ON (product_id) *
                        FROM recommendation_events
                        WHERE user_id = :userId
                          AND product_id IN (:productIds)
                          AND event_type = 'CLICK'
                          AND created_at <= :orderCreatedAt
                        ORDER BY product_id, created_at DESC, id DESC
                        """, RecommendationEvent.class)
                .setParameter("userId", userId)
                .setParameter("productIds", productIds)
                .setParameter("orderCreatedAt", orderCreatedAt)
                .getResultList();
        return events;
    }

    public Metrics overview(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String recommendationType
    ) {
        String typeFilter = recommendationType == null
                ? ""
                : " AND event.recommendation_type = :recommendationType";
        Query query = entityManager.createNativeQuery("""
                        SELECT
                            count(*) FILTER (WHERE event.event_type = 'IMPRESSION'),
                            count(*) FILTER (WHERE event.event_type = 'CLICK'),
                            count(*) FILTER (WHERE event.event_type = 'VIEW'),
                            count(*) FILTER (WHERE event.event_type = 'ADD_TO_CART'),
                            count(*) FILTER (
                                WHERE event.event_type = 'PURCHASE'
                                  AND order_row.id IS NOT NULL
                                  AND upper(order_row.status) <> 'CANCELLED'
                                  AND upper(order_row.payment_status) = 'PAID'
                                  AND EXISTS (
                                      SELECT 1 FROM payments payment_row
                                      WHERE payment_row.order_id = order_row.id
                                        AND upper(payment_row.status) = 'PAID'
                                  )
                            )
                        FROM recommendation_events event
                        LEFT JOIN orders order_row ON order_row.id = event.order_id
                        WHERE event.created_at >= :fromAt
                          AND event.created_at < :toExclusive
                        """ + typeFilter)
                .setParameter("fromAt", from)
                .setParameter("toExclusive", toExclusive);
        setType(query, recommendationType);
        Object[] row = (Object[]) query.getSingleResult();
        return new Metrics(
                number(row[0]), number(row[1]), number(row[2]), number(row[3]), number(row[4]));
    }

    public List<ProductMetrics> products(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String recommendationType,
            int limit
    ) {
        String typeFilter = recommendationType == null
                ? ""
                : " AND event.recommendation_type = :recommendationType";
        Query query = entityManager.createNativeQuery("""
                        SELECT product.id,
                               product.name,
                               count(*) FILTER (WHERE event.event_type = 'IMPRESSION'),
                               count(*) FILTER (WHERE event.event_type = 'CLICK'),
                               count(*) FILTER (WHERE event.event_type = 'VIEW'),
                               count(*) FILTER (WHERE event.event_type = 'ADD_TO_CART'),
                               count(*) FILTER (
                                   WHERE event.event_type = 'PURCHASE'
                                     AND order_row.id IS NOT NULL
                                     AND upper(order_row.status) <> 'CANCELLED'
                                     AND upper(order_row.payment_status) = 'PAID'
                                     AND EXISTS (
                                         SELECT 1 FROM payments payment_row
                                         WHERE payment_row.order_id = order_row.id
                                           AND upper(payment_row.status) = 'PAID'
                                     )
                               )
                        FROM recommendation_events event
                        JOIN products product ON product.id = event.product_id
                        LEFT JOIN orders order_row ON order_row.id = event.order_id
                        WHERE event.created_at >= :fromAt
                          AND event.created_at < :toExclusive
                        """ + typeFilter + "\n" + """
                        GROUP BY product.id, product.name
                        ORDER BY product.name ASC, product.id ASC
                        """)
                .setParameter("fromAt", from)
                .setParameter("toExclusive", toExclusive)
                .setMaxResults(limit);
        setType(query, recommendationType);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new ProductMetrics(
                        (UUID) row[0], (String) row[1], number(row[2]), number(row[3]),
                        number(row[4]), number(row[5]), number(row[6])))
                .toList();
    }

    public List<TypeMetrics> types(LocalDateTime from, LocalDateTime toExclusive) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT event.recommendation_type,
                               count(*) FILTER (WHERE event.event_type = 'IMPRESSION'),
                               count(*) FILTER (WHERE event.event_type = 'CLICK'),
                               count(*) FILTER (WHERE event.event_type = 'VIEW'),
                               count(*) FILTER (WHERE event.event_type = 'ADD_TO_CART'),
                               count(*) FILTER (
                                   WHERE event.event_type = 'PURCHASE'
                                     AND order_row.id IS NOT NULL
                                     AND upper(order_row.status) <> 'CANCELLED'
                                     AND upper(order_row.payment_status) = 'PAID'
                                     AND EXISTS (
                                         SELECT 1 FROM payments payment_row
                                         WHERE payment_row.order_id = order_row.id
                                           AND upper(payment_row.status) = 'PAID'
                                     )
                               )
                        FROM recommendation_events event
                        LEFT JOIN orders order_row ON order_row.id = event.order_id
                        WHERE event.created_at >= :fromAt
                          AND event.created_at < :toExclusive
                        GROUP BY event.recommendation_type
                        """)
                .setParameter("fromAt", from)
                .setParameter("toExclusive", toExclusive)
                .getResultList();
        return rows.stream()
                .map(row -> new TypeMetrics(
                        (String) row[0], number(row[1]), number(row[2]), number(row[3]),
                        number(row[4]), number(row[5])))
                .toList();
    }

    private void setType(Query query, String recommendationType) {
        if (recommendationType != null) {
            query.setParameter("recommendationType", recommendationType);
        }
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    public record Metrics(
            long impressions, long clicks, long views, long addToCarts, long conversions
    ) {
    }

    public record ProductMetrics(
            UUID productId,
            String productName,
            long impressions,
            long clicks,
            long views,
            long addToCarts,
            long conversions
    ) {
    }

    public record TypeMetrics(
            String recommendationType,
            long impressions,
            long clicks,
            long views,
            long addToCarts,
            long conversions
    ) {
    }
}
