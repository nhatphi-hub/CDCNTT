package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.dto.datamining.CoOccurrenceResponse;
import com.smart_ecommerce.backend.dto.datamining.FrequentItemResponse;
import com.smart_ecommerce.backend.dto.datamining.RfmCustomerResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public class DataMiningRepository {

    private static final String ELIGIBLE_ORDER_PREDICATE = """
            o.created_at >= :fromAt AND o.created_at < :toExclusive
              AND upper(o.status) <> 'CANCELLED'
              AND upper(o.payment_status) = 'PAID'
            """;

    @PersistenceContext
    private EntityManager entityManager;

    public long countRfmCustomers(java.time.LocalDateTime fromAt,
                                  java.time.LocalDateTime toExclusive) {
        Number count = (Number) entityManager.createNativeQuery("""
                        SELECT count(DISTINCT o.user_id)
                        FROM orders o
                        WHERE o.created_at >= :fromAt AND o.created_at < :toExclusive
                          AND upper(o.status) <> 'CANCELLED'
                          AND upper(o.payment_status) = 'PAID'
                        """)
                .setParameter("fromAt", fromAt)
                .setParameter("toExclusive", toExclusive)
                .getSingleResult();
        return count.longValue();
    }

    public List<RfmCustomerResponse> findRfmCustomers(
            java.time.LocalDateTime fromAt,
            java.time.LocalDateTime toExclusive,
            java.time.LocalDate referenceDate,
            int offset,
            int limit
    ) {
        Query query = entityManager.createNativeQuery("""
                WITH customer_rfm AS (
                    SELECT o.user_id AS customer_id,
                           MAX(o.created_at) AS latest_purchase_at,
                           count(o.id) AS frequency,
                           sum(o.total_amount) AS monetary
                    FROM orders o
                    WHERE o.created_at >= :fromAt AND o.created_at < :toExclusive
                      AND upper(o.status) <> 'CANCELLED'
                      AND upper(o.payment_status) = 'PAID'
                    GROUP BY o.user_id
                ), ranked AS (
                    SELECT customer_id,
                           CAST(CAST(:referenceDate AS date)
                                - CAST(latest_purchase_at AS date) AS integer) AS recency_days,
                           frequency,
                           monetary,
                           count(*) OVER () AS customer_count,
                           percent_rank() OVER (ORDER BY latest_purchase_at ASC)
                               AS recency_percentile,
                           percent_rank() OVER (ORDER BY frequency ASC)
                               AS frequency_percentile,
                           percent_rank() OVER (ORDER BY monetary ASC)
                               AS monetary_percentile
                    FROM customer_rfm
                ), scored AS (
                    SELECT customer_id,
                           recency_days,
                           frequency,
                           monetary,
                           customer_count,
                           CASE WHEN recency_percentile >= 0.8 THEN 5
                                WHEN recency_percentile >= 0.6 THEN 4
                                WHEN recency_percentile >= 0.4 THEN 3
                                WHEN recency_percentile >= 0.2 THEN 2
                                ELSE 1 END AS recency_score,
                           CASE WHEN frequency_percentile >= 0.8 THEN 5
                                WHEN frequency_percentile >= 0.6 THEN 4
                                WHEN frequency_percentile >= 0.4 THEN 3
                                WHEN frequency_percentile >= 0.2 THEN 2
                                ELSE 1 END AS frequency_score,
                           CASE WHEN monetary_percentile >= 0.8 THEN 5
                                WHEN monetary_percentile >= 0.6 THEN 4
                                WHEN monetary_percentile >= 0.4 THEN 3
                                WHEN monetary_percentile >= 0.2 THEN 2
                                ELSE 1 END AS monetary_score
                    FROM ranked
                )
                SELECT customer_id,
                       recency_days,
                       frequency,
                       monetary,
                       CASE WHEN customer_count >= 5 THEN recency_score END,
                       CASE WHEN customer_count >= 5 THEN frequency_score END,
                       CASE WHEN customer_count >= 5 THEN monetary_score END,
                       CASE WHEN customer_count >= 5 THEN
                           concat(recency_score, frequency_score, monetary_score)
                       END AS rfm_score,
                       CASE WHEN customer_count < 5 THEN NULL
                            WHEN recency_score >= 4 AND frequency_score >= 4
                                 AND monetary_score >= 4 THEN 'CHAMPIONS'
                            WHEN frequency_score >= 4 AND recency_score >= 3
                                 THEN 'LOYAL_CUSTOMERS'
                            WHEN recency_score >= 4 AND frequency_score >= 2
                                 THEN 'POTENTIAL_LOYALISTS'
                            WHEN recency_score <= 2 AND frequency_score >= 3
                                 THEN 'AT_RISK'
                            ELSE 'NEEDS_ATTENTION' END AS segment
                FROM scored
                ORDER BY monetary DESC, customer_id ASC
                """)
                .setParameter("fromAt", fromAt)
                .setParameter("toExclusive", toExclusive)
                .setParameter("referenceDate", referenceDate)
                .setFirstResult(offset)
                .setMaxResults(limit);

        List<Object[]> rows = query.getResultList();
        List<RfmCustomerResponse> customers = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            customers.add(new RfmCustomerResponse(
                    (UUID) row[0],
                    ((Number) row[1]).intValue(),
                    ((Number) row[2]).longValue(),
                    (BigDecimal) row[3],
                    nullableInteger(row[4]),
                    nullableInteger(row[5]),
                    nullableInteger(row[6]),
                    (String) row[7],
                    (String) row[8]));
        }
        return customers;
    }

    public long countEligibleTransactions(
            java.time.LocalDateTime fromAt,
            java.time.LocalDateTime toExclusive
    ) {
        Number count = (Number) entityManager.createNativeQuery("""
                        SELECT count(*)
                        FROM orders o
                        WHERE
                        """ + ELIGIBLE_ORDER_PREDICATE)
                .setParameter("fromAt", fromAt)
                .setParameter("toExclusive", toExclusive)
                .getSingleResult();
        return count.longValue();
    }

    public List<PairAggregate> findFrequentPairs(
            java.time.LocalDateTime fromAt,
            java.time.LocalDateTime toExclusive,
            long minimumTransactionCount,
            int limit
    ) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT first_product.id,
                               first_product.name,
                               second_product.id,
                               second_product.name,
                               count(DISTINCT o.id) AS transaction_count
                        FROM orders o
                        JOIN order_items first_item ON first_item.order_id = o.id
                        JOIN order_items second_item ON second_item.order_id = o.id
                        JOIN products first_product ON first_product.id = first_item.product_id
                        JOIN products second_product ON second_product.id = second_item.product_id
                        WHERE
                        """ + ELIGIBLE_ORDER_PREDICATE + """
                          AND first_item.product_id < second_item.product_id
                        GROUP BY first_product.id, first_product.name,
                                 second_product.id, second_product.name
                        HAVING count(DISTINCT o.id) >= :minimumTransactionCount
                        ORDER BY transaction_count DESC,
                                 first_product.id ASC, second_product.id ASC
                        """)
                .setParameter("fromAt", fromAt)
                .setParameter("toExclusive", toExclusive)
                .setParameter("minimumTransactionCount", minimumTransactionCount)
                .setMaxResults(limit)
                .getResultList();
        List<PairAggregate> pairs = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            pairs.add(new PairAggregate(
                    (UUID) row[0], (String) row[1],
                    (UUID) row[2], (String) row[3],
                    ((Number) row[4]).longValue()));
        }
        return pairs;
    }

    public long countOrdersContainingProduct(
            UUID productId,
            java.time.LocalDateTime fromAt,
            java.time.LocalDateTime toExclusive
    ) {
        Number count = (Number) entityManager.createNativeQuery("""
                        SELECT count(DISTINCT o.id)
                        FROM orders o
                        JOIN order_items item ON item.order_id = o.id
                        WHERE
                        """ + ELIGIBLE_ORDER_PREDICATE + """
                          AND item.product_id = :productId
                        """)
                .setParameter("fromAt", fromAt)
                .setParameter("toExclusive", toExclusive)
                .setParameter("productId", productId)
                .getSingleResult();
        return count.longValue();
    }

    public List<CoOccurrenceAggregate> findCoOccurrences(
            UUID productId,
            java.time.LocalDateTime fromAt,
            java.time.LocalDateTime toExclusive,
            int limit
    ) {
        return findCoOccurrences(productId, fromAt, toExclusive, limit, false);
    }

    public List<CoOccurrenceAggregate> findActiveCoOccurrences(
            UUID productId,
            java.time.LocalDateTime fromAt,
            java.time.LocalDateTime toExclusive,
            int limit
    ) {
        return findCoOccurrences(productId, fromAt, toExclusive, limit, true);
    }

    private List<CoOccurrenceAggregate> findCoOccurrences(
            UUID productId,
            java.time.LocalDateTime fromAt,
            java.time.LocalDateTime toExclusive,
            int limit,
            boolean activeCandidatesOnly
    ) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT CASE WHEN first_item.product_id = :productId
                                    THEN second_product.id ELSE first_product.id END
                                   AS related_product_id,
                               CASE WHEN first_item.product_id = :productId
                                    THEN second_product.name ELSE first_product.name END
                                   AS related_product_name,
                               count(DISTINCT o.id) AS transaction_count
                        FROM orders o
                        JOIN order_items first_item ON first_item.order_id = o.id
                        JOIN order_items second_item ON second_item.order_id = o.id
                        JOIN products first_product ON first_product.id = first_item.product_id
                        JOIN products second_product ON second_product.id = second_item.product_id
                        WHERE
                        """ + ELIGIBLE_ORDER_PREDICATE + """
                          AND first_item.product_id <> second_item.product_id
                          AND (first_item.product_id = :productId
                               OR second_item.product_id = :productId)
                          AND (:activeCandidatesOnly = false
                               OR (first_item.product_id = :productId
                                   AND upper(second_product.status) = 'ACTIVE')
                               OR (second_item.product_id = :productId
                                   AND upper(first_product.status) = 'ACTIVE'))
                        GROUP BY related_product_id, related_product_name
                        ORDER BY transaction_count DESC, related_product_id ASC
                        """)
                .setParameter("fromAt", fromAt)
                .setParameter("toExclusive", toExclusive)
                .setParameter("productId", productId)
                .setParameter("activeCandidatesOnly", activeCandidatesOnly)
                .setMaxResults(limit)
                .getResultList();
        List<CoOccurrenceAggregate> aggregates = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            aggregates.add(new CoOccurrenceAggregate(
                    (UUID) row[0], (String) row[1], ((Number) row[2]).longValue()));
        }
        return aggregates;
    }

    /**
     * Batch co-occurrence query used by personalization so purchase/view seeds
     * do not cause one database round trip per product.
     */
    public List<SeedCoOccurrenceAggregate> findCoOccurrencesForProducts(
            Collection<UUID> sourceProductIds,
            java.time.LocalDateTime fromAt,
            java.time.LocalDateTime toExclusive,
            int limit
    ) {
        if (sourceProductIds.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows = entityManager.createNativeQuery("""
                        WITH source_frequency AS (
                            SELECT item.product_id, count(DISTINCT o.id) AS order_count
                            FROM orders o
                            JOIN order_items item ON item.order_id = o.id
                            WHERE
                            """ + ELIGIBLE_ORDER_PREDICATE + """
                              AND item.product_id IN (:sourceProductIds)
                            GROUP BY item.product_id
                        ), pair_frequency AS (
                            SELECT source.product_id AS source_product_id,
                                   candidate.product_id AS candidate_product_id,
                                   count(DISTINCT o.id) AS transaction_count
                            FROM orders o
                            JOIN order_items source ON source.order_id = o.id
                            JOIN order_items candidate ON candidate.order_id = o.id
                            JOIN products visible_candidate
                              ON visible_candidate.id = candidate.product_id
                             AND upper(visible_candidate.status) = 'ACTIVE'
                            WHERE
                            """ + ELIGIBLE_ORDER_PREDICATE + """
                              AND source.product_id IN (:sourceProductIds)
                              AND source.product_id <> candidate.product_id
                            GROUP BY source.product_id, candidate.product_id
                        )
                        SELECT pairs.source_product_id,
                               pairs.candidate_product_id,
                               product.name,
                               pairs.transaction_count,
                               frequency.order_count
                        FROM pair_frequency pairs
                        JOIN products product ON product.id = pairs.candidate_product_id
                        JOIN source_frequency frequency
                          ON frequency.product_id = pairs.source_product_id
                        ORDER BY pairs.transaction_count DESC,
                                 pairs.source_product_id ASC,
                                 pairs.candidate_product_id ASC
                        """)
                .setParameter("fromAt", fromAt)
                .setParameter("toExclusive", toExclusive)
                .setParameter("sourceProductIds", sourceProductIds)
                .setMaxResults(limit)
                .getResultList();
        List<SeedCoOccurrenceAggregate> aggregates = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            aggregates.add(new SeedCoOccurrenceAggregate(
                    (UUID) row[0], (UUID) row[1], (String) row[2],
                    ((Number) row[3]).longValue(), ((Number) row[4]).longValue()));
        }
        return aggregates;
    }

    private static Integer nullableInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    public record PairAggregate(
            UUID productId1,
            String productName1,
            UUID productId2,
            String productName2,
            long transactionCount
    ) {
    }

    public record CoOccurrenceAggregate(
            UUID productId,
            String productName,
            long transactionCount
    ) {
    }

    public record SeedCoOccurrenceAggregate(
            UUID sourceProductId,
            UUID productId,
            String productName,
            long transactionCount,
            long sourceTransactionCount
    ) {
    }

    public static BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }
}
