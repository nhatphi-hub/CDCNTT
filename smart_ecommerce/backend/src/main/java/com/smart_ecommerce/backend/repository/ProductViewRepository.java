package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.ProductView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductViewRepository extends JpaRepository<ProductView, UUID> {

    List<ProductView> findByUserIdOrderByViewedAtDesc(UUID userId);

    List<ProductView> findByProductIdOrderByViewedAtDesc(UUID productId);

    long countByProductId(UUID productId);

    long countByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
