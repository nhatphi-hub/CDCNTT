package com.smart_ecommerce.backend.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public class RecommendationRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** Resolve descendant categories to their root for cross-subcategory interest. */
    public java.util.Map<UUID, UUID> findCategoryRoots() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT id, parent_id FROM categories").getResultList();
        java.util.Map<UUID, UUID> parents = new java.util.HashMap<>();
        for (Object[] row : rows) parents.put((UUID) row[0], (UUID) row[1]);
        java.util.Map<UUID, UUID> roots = new java.util.HashMap<>();
        for (UUID id : parents.keySet()) {
            UUID current = id;
            java.util.Set<UUID> visited = new java.util.HashSet<>();
            while (parents.get(current) != null && visited.add(current)) {
                current = parents.get(current);
            }
            roots.put(id, current);
        }
        return roots;
    }

    /** Recent real searches, per authenticated customer. No user id is supplied by clients. */
    public List<String> findSearchKeywords(UUID userId, int limit) {
        return entityManager.createQuery("""
                        select history.keyword from SearchHistory history
                        where history.user.id = :userId
                        order by history.searchedAt desc
                        """, String.class)
                .setParameter("userId", userId)
                .setMaxResults(limit)
                .getResultList();
    }

    /** Search on visible catalog only, including parent category name. */
    public List<UUID> findActiveProductIdsByKeyword(String keyword, int limit) {
        String pattern = "%" + keyword.toLowerCase(java.util.Locale.ROOT) + "%";
        return entityManager.createQuery("""
                        select product.id from Product product
                        left join product.shop sellerShop
                        left join product.category category
                        left join category.parent parentCategory
                        where upper(product.status) = 'ACTIVE'
                          and (sellerShop.id is null or upper(sellerShop.status) = 'ACTIVE')
                          and (lower(product.name) like :pattern
                            or lower(coalesce(category.name, '')) like :pattern
                            or lower(coalesce(parentCategory.name, '')) like :pattern
                            or lower(coalesce(product.productType, '')) like :pattern)
                        order by product.createdAt desc, product.id asc
                        """, UUID.class)
                .setParameter("pattern", pattern)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<UUID> findActiveProductIdsByCategories(
            Collection<UUID> categoryIds,
            Collection<UUID> excludedProductIds,
            int limit
    ) {
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select product.id
                        from Product product left join product.shop sellerShop
                        where (sellerShop.id is null or upper(sellerShop.status) = 'ACTIVE')
                          and upper(product.status) = 'ACTIVE'
                          and product.id not in :excludedProductIds
                          and product.category.id in :categoryIds
                        order by product.name asc, product.id asc
                        """, UUID.class)
                .setParameter("excludedProductIds", excludedProductIds.isEmpty()
                        ? java.util.Set.of(new UUID(0L, 0L)) : excludedProductIds)
                .setParameter("categoryIds", categoryIds)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<UUID> findActiveProductIdsByTypes(
            Collection<String> productTypes,
            Collection<UUID> excludedProductIds,
            int limit
    ) {
        if (productTypes.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select product.id
                        from Product product left join product.shop sellerShop
                        where (sellerShop.id is null or upper(sellerShop.status) = 'ACTIVE')
                          and upper(product.status) = 'ACTIVE'
                          and product.id not in :excludedProductIds
                          and lower(product.productType) in :productTypes
                        order by product.name asc, product.id asc
                        """, UUID.class)
                .setParameter("excludedProductIds", excludedProductIds.isEmpty()
                        ? java.util.Set.of(new UUID(0L, 0L)) : excludedProductIds)
                .setParameter("productTypes", productTypes)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<UUID> findActiveProductIdsByGenders(
            Collection<String> genders,
            Collection<UUID> excludedProductIds,
            int limit
    ) {
        if (genders.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select product.id
                        from Product product left join product.shop sellerShop
                        where (sellerShop.id is null or upper(sellerShop.status) = 'ACTIVE')
                          and upper(product.status) = 'ACTIVE'
                          and product.id not in :excludedProductIds
                          and lower(product.gender) in :genders
                        order by product.name asc, product.id asc
                        """, UUID.class)
                .setParameter("excludedProductIds", excludedProductIds.isEmpty()
                        ? java.util.Set.of(new UUID(0L, 0L)) : excludedProductIds)
                .setParameter("genders", genders)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<ProductProfile> findProfilesByIds(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT p.id, p.category_id, p.product_type, p.gender
                        FROM products p
                        WHERE p.id IN (:productIds)
                        """)
                .setParameter("productIds", productIds)
                .getResultList();
        List<ProductProfile> profiles = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            profiles.add(new ProductProfile(
                    (UUID) row[0], (UUID) row[1], (String) row[2], (String) row[3]));
        }
        return profiles;
    }

    public List<PurchaseSignal> findPurchaseSignals(
            UUID userId,
            LocalDateTime fromAt,
            LocalDateTime toExclusive,
            int limit
    ) {
        List<Object[]> rows = entityManager.createQuery("""
                        select item.product.id, count(distinct ord.id), sum(item.quantity)
                        from OrderItem item
                        join item.order ord
                        where ord.user.id = :userId
                          and ord.createdAt >= :fromAt and ord.createdAt < :toExclusive
                          and upper(ord.status) <> 'CANCELLED'
                          and upper(ord.paymentStatus) = 'PAID'
                        group by item.product.id
                        order by count(distinct ord.id) desc, sum(item.quantity) desc,
                                 item.product.id asc
                        """, Object[].class)
                .setParameter("userId", userId)
                .setParameter("fromAt", fromAt)
                .setParameter("toExclusive", toExclusive)
                .setMaxResults(limit)
                .getResultList();
        List<PurchaseSignal> signals = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            signals.add(new PurchaseSignal(
                    (UUID) row[0], ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue()));
        }
        return signals;
    }

    public List<ViewSignal> findViewSignals(UUID userId, int limit) {
        List<Object[]> rows = entityManager.createQuery("""
                        select view.product.id, count(view.id)
                        from ProductView view
                        where view.user.id = :userId
                        group by view.product.id
                        order by count(view.id) desc, view.product.id asc
                        """, Object[].class)
                .setParameter("userId", userId)
                .setMaxResults(limit)
                .getResultList();
        List<ViewSignal> signals = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            signals.add(new ViewSignal((UUID) row[0], ((Number) row[1]).longValue()));
        }
        return signals;
    }

    /**
     * Implicit feedback from actual customer activity. Impression events are excluded:
     * seeing a recommendation is not evidence that the customer prefers it.
     * Favorited / cart items are current intent, so unlike past views they do not
     * expire after the observation window. All signals are scoped by user ID.
     */
    public List<ImplicitSignal> findImplicitSignals(
            UUID userId, LocalDateTime viewedSince, int limit
    ) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT signal.product_id, sum(signal.weight)::numeric AS signal_weight,
                       max(signal.last_at) AS last_at
                FROM (
                    SELECT pv.product_id,
                           least(count(*), 8)::numeric AS weight,
                           max(pv.viewed_at) AS last_at
                    FROM product_views pv
                    WHERE pv.user_id = :userId AND pv.viewed_at >= :viewedSince
                    GROUP BY pv.product_id
                    UNION ALL
                    SELECT f.product_id, 5::numeric AS weight, max(f.created_at) AS last_at
                    FROM favorites f WHERE f.user_id = :userId
                    GROUP BY f.product_id
                    UNION ALL
                    SELECT ci.product_id,
                           least(sum(ci.quantity), 4)::numeric * 2 AS weight,
                           max(ci.updated_at) AS last_at
                    FROM cart_items ci JOIN carts c ON c.id = ci.cart_id
                    WHERE c.user_id = :userId
                    GROUP BY ci.product_id
                    UNION ALL
                    SELECT ev.product_id,
                           least(sum(CASE WHEN ev.event_type = 'ADD_TO_CART' THEN 3 ELSE 2 END), 12)::numeric AS weight,
                           max(ev.created_at) AS last_at
                    FROM recommendation_events ev
                    WHERE ev.user_id = :userId AND ev.created_at >= :viewedSince
                      AND ev.event_type IN ('CLICK', 'ADD_TO_CART')
                    GROUP BY ev.product_id
                ) signal
                GROUP BY signal.product_id
                ORDER BY signal_weight DESC, signal.product_id ASC
                """)
                .setParameter("userId", userId)
                .setParameter("viewedSince", viewedSince)
                .setMaxResults(limit)
                .getResultList();
        List<ImplicitSignal> signals = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Object timestamp = row[2];
            LocalDateTime lastAt = timestamp instanceof LocalDateTime value
                    ? value : ((java.sql.Timestamp) timestamp).toLocalDateTime();
            signals.add(new ImplicitSignal(
                    (UUID) row[0], ((Number) row[1]).doubleValue(), lastAt));
        }
        return signals;
    }

    /** Recent active catalog items when there are not yet purchases or views. */
    public List<ProductData> findCatalogFallback(int limit) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT p.id, p.name, p.slug, image.image_url, p.price, p.sale_price,
                       p.category_id, p.product_type, p.gender,
                       0::bigint, 0::bigint
                FROM products p
                LEFT JOIN LATERAL (
                    SELECT pi.image_url FROM product_images pi
                    WHERE pi.product_id = p.id
                    ORDER BY pi.is_primary DESC, pi.sort_order ASC, pi.id ASC LIMIT 1
                ) image ON TRUE
                WHERE upper(p.status) = 'ACTIVE'
                  AND (p.shop_id IS NULL OR EXISTS (
                      SELECT 1 FROM shops sh WHERE sh.id = p.shop_id AND upper(sh.status) = 'ACTIVE'
                  ))
                ORDER BY p.created_at DESC NULLS LAST, p.id ASC
                """)
                .setMaxResults(limit)
                .getResultList();
        return rows.stream().map(RecommendationRepository::toProductData).toList();
    }

    public List<ProductData> findActiveProductDataByIds(
            Collection<UUID> productIds,
            LocalDateTime fromAt,
            LocalDateTime toExclusive
    ) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows = entityManager.createNativeQuery("""
                        WITH sales AS (
                            SELECT item.product_id,
                                   sum(item.quantity)::bigint AS units_sold
                            FROM orders ord
                            JOIN order_items item ON item.order_id = ord.id
                            WHERE ord.created_at >= :fromAt AND ord.created_at < :toExclusive
                              AND upper(ord.status) <> 'CANCELLED'
                              AND upper(ord.payment_status) = 'PAID'
                              AND item.product_id IN (:productIds)
                            GROUP BY item.product_id
                        ), views AS (
                            SELECT view.product_id, count(view.id)::bigint AS view_count
                            FROM product_views view
                            WHERE view.viewed_at >= :fromAt AND view.viewed_at < :toExclusive
                              AND view.product_id IN (:productIds)
                            GROUP BY view.product_id
                        )
                        SELECT p.id,
                               p.name,
                               p.slug,
                               image.image_url,
                               p.price,
                               p.sale_price,
                               p.category_id,
                               p.product_type,
                               p.gender,
                               coalesce(sales.units_sold, 0),
                               coalesce(views.view_count, 0)
                        FROM products p
                        LEFT JOIN sales ON sales.product_id = p.id
                        LEFT JOIN views ON views.product_id = p.id
                        LEFT JOIN LATERAL (
                            SELECT pi.image_url
                            FROM product_images pi
                            WHERE pi.product_id = p.id
                            ORDER BY pi.is_primary DESC, pi.sort_order ASC, pi.id ASC
                            LIMIT 1
                        ) image ON TRUE
                        WHERE p.id IN (:productIds)
                          AND upper(p.status) = 'ACTIVE'
                          AND (p.shop_id IS NULL OR upper((SELECT sh.status FROM shops sh WHERE sh.id = p.shop_id)) = 'ACTIVE')
                        ORDER BY p.id ASC
                        """)
                .setParameter("fromAt", fromAt)
                .setParameter("toExclusive", toExclusive)
                .setParameter("productIds", productIds)
                .getResultList();
        List<ProductData> products = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            products.add(toProductData(row));
        }
        return products;
    }

    public List<PopularProductData> findPopularProducts(
            LocalDateTime fromAt,
            LocalDateTime toExclusive,
            int limit
    ) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        WITH sales AS (
                            SELECT item.product_id,
                                   sum(item.quantity)::bigint AS units_sold,
                                   count(DISTINCT ord.id)::bigint AS order_count
                            FROM orders ord
                            JOIN order_items item ON item.order_id = ord.id
                            WHERE ord.created_at >= :fromAt AND ord.created_at < :toExclusive
                              AND upper(ord.status) <> 'CANCELLED'
                              AND upper(ord.payment_status) = 'PAID'
                            GROUP BY item.product_id
                        ), views AS (
                            SELECT view.product_id, count(view.id)::bigint AS view_count
                            FROM product_views view
                            WHERE view.viewed_at >= :fromAt AND view.viewed_at < :toExclusive
                            GROUP BY view.product_id
                        ), candidates AS (
                            SELECT p.id AS product_id,
                                   p.name AS product_name,
                                   p.slug,
                                   image.image_url,
                                   p.price,
                                   p.sale_price,
                                   coalesce(sales.units_sold, 0)::bigint AS units_sold,
                                   coalesce(sales.order_count, 0)::bigint AS order_count,
                                   coalesce(views.view_count, 0)::bigint AS view_count
                            FROM products p
                            LEFT JOIN sales ON sales.product_id = p.id
                            LEFT JOIN views ON views.product_id = p.id
                            LEFT JOIN LATERAL (
                                SELECT pi.image_url
                                FROM product_images pi
                                WHERE pi.product_id = p.id
                                ORDER BY pi.is_primary DESC, pi.sort_order ASC, pi.id ASC
                                LIMIT 1
                            ) image ON TRUE
                            WHERE upper(p.status) = 'ACTIVE'
                              AND (p.shop_id IS NULL OR upper((SELECT sh.status FROM shops sh WHERE sh.id = p.shop_id)) = 'ACTIVE')
                              AND (coalesce(sales.units_sold, 0) > 0
                                   OR coalesce(views.view_count, 0) > 0)
                        ), scored AS (
                            SELECT candidates.*,
                                   CASE
                                       WHEN max(units_sold) OVER () > 0
                                            AND max(view_count) OVER () > 0
                                           THEN (units_sold::numeric / max(units_sold) OVER ()
                                               + view_count::numeric / max(view_count) OVER ()) / 2
                                       WHEN max(units_sold) OVER () > 0
                                           THEN units_sold::numeric / max(units_sold) OVER ()
                                       ELSE view_count::numeric / max(view_count) OVER ()
                                   END AS popularity_score
                            FROM candidates
                        )
                        SELECT product_id, product_name, slug, image_url, price, sale_price,
                               units_sold, view_count, popularity_score
                        FROM scored
                        ORDER BY popularity_score DESC, units_sold DESC, view_count DESC,
                                 product_id ASC
                        """)
                .setParameter("fromAt", fromAt)
                .setParameter("toExclusive", toExclusive)
                .setMaxResults(limit)
                .getResultList();
        List<PopularProductData> products = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            products.add(new PopularProductData(
                    new ProductData(
                            (UUID) row[0], (String) row[1], (String) row[2], (String) row[3],
                            (BigDecimal) row[4], (BigDecimal) row[5], null, null, null,
                            ((Number) row[6]).longValue(), ((Number) row[7]).longValue()),
                    ((Number) row[8]).doubleValue()));
        }
        return products;
    }

    private static ProductData toProductData(Object[] row) {
        return new ProductData(
                (UUID) row[0], (String) row[1], (String) row[2], (String) row[3],
                (BigDecimal) row[4], (BigDecimal) row[5],
                (UUID) row[6], (String) row[7], (String) row[8],
                ((Number) row[9]).longValue(), ((Number) row[10]).longValue());
    }

    public record ProductProfile(
            UUID productId,
            UUID categoryId,
            String productType,
            String gender
    ) {
    }

    public record ImplicitSignal(UUID productId, double strength, LocalDateTime lastAt) {
    }

    public record PurchaseSignal(UUID productId, long orderCount, long quantityPurchased) {
    }

    public record ViewSignal(UUID productId, long viewCount) {
    }

    public record ProductData(
            UUID productId,
            String productName,
            String slug,
            String imageUrl,
            BigDecimal price,
            BigDecimal salePrice,
            UUID categoryId,
            String productType,
            String gender,
            long unitsSold,
            long viewCount
    ) {
    }

    public record PopularProductData(ProductData product, double popularityScore) {
    }
}
