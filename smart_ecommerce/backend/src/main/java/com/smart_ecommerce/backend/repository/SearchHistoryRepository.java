package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, UUID> {
    List<SearchHistory> findByUserIdOrderBySearchedAtDesc(UUID userId);
    Optional<SearchHistory> findByIdAndUserId(UUID id, UUID userId);
    long countByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
