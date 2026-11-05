package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository
        extends JpaRepository<CartItem, UUID> {

    Optional<CartItem> findByIdAndCartUserId(
            UUID itemId,
            UUID userId
    );

    Optional<CartItem> findByCartIdAndProductIdAndVariantId(
            UUID cartId,
            UUID productId,
            UUID variantId
    );

    boolean existsByVariantId(UUID variantId);
}