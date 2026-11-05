package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.OrderItem;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    @Query("select case when count(item) > 0 then true else false end "
            + "from OrderItem item where item.variant.id = :variantId "
            + "or item.variantIdSnapshot = :variantId")
    boolean existsByVariantReference(@Param("variantId") UUID variantId);

    // Ownership comes from authenticated user, not from any client-provided order ID.
    @Query("""
        select case when count(item) > 0 then true else false end
        from OrderItem item join item.order o
        where o.user.id = :userId and item.product.id = :productId
          and upper(o.status) = 'DELIVERED' and upper(o.paymentStatus) = 'PAID'
        """)
    boolean hasCompletedPurchase(@Param("userId") UUID userId,
                                 @Param("productId") UUID productId);
}
