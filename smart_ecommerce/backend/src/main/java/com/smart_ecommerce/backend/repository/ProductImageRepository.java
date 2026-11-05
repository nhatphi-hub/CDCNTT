package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderByIsPrimaryDescSortOrderAsc(UUID productId);

    Optional<ProductImage> findFirstByProductIdOrderByIsPrimaryDescSortOrderAsc(UUID productId);

    boolean existsByProductIdAndIsPrimaryTrue(UUID productId);

    void deleteByProductId(UUID productId);
}
