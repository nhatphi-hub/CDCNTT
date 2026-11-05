package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ShopRepository extends JpaRepository<Shop, UUID> {
    org.springframework.data.domain.Page<Shop> findByStatusIgnoreCase(String status, org.springframework.data.domain.Pageable pageable);

    Optional<Shop> findByOwnerId(UUID ownerId);
    boolean existsByOwnerId(UUID ownerId);
    List<Shop> findByStatusOrderByCreatedAtAsc(String status);
}
