package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    List<Favorite> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Favorite> findByUserIdAndProductId(
            UUID userId,
            UUID productId
    );

    boolean existsByUserIdAndProductId(
            UUID userId,
            UUID productId
    );

    void deleteByUserIdAndProductId(
            UUID userId,
            UUID productId
    );
}
