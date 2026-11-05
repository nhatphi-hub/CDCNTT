package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository
        extends JpaRepository<ProductVariant, UUID> {

    Optional<ProductVariant> findByIdAndProductShopId(UUID id, UUID shopId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.id = :id")
    Optional<ProductVariant> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByProductId(UUID productId);

    List<ProductVariant> findByProductId(
            UUID productId
    );

    List<ProductVariant> findByProductIdAndStatus(
            UUID productId,
            String status
    );

    List<ProductVariant> findByProductIdAndStatusIgnoreCase(UUID productId, String status);

    Optional<ProductVariant> findByIdAndStatusIgnoreCase(UUID id, String status);

    Optional<ProductVariant> findBySku(
            String sku
    );

    boolean existsBySku(
            String sku
    );
}
