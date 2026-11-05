package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByProductIdOrderByCreatedAtDesc(UUID productId);

    Optional<Review> findByUserIdAndProductId(UUID userId, UUID productId);

    List<Review> findByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    @Query("select r from Review r join fetch r.user join fetch r.product p "
            + "where p.shop.id = :shopId order by r.createdAt desc")
    List<Review> findReviewsOfShop(@Param("shopId") UUID shopId);
}
