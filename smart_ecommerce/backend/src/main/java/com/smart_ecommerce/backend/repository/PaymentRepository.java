package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    org.springframework.data.domain.Page<Payment> findByStatusIgnoreCase(String status, org.springframework.data.domain.Pageable pageable);


    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByOrder_IdOrderByCreatedAtDesc(UUID orderId);

    Optional<Payment> findFirstByOrder_IdAndProviderOrderByCreatedAtDesc(
            UUID orderId,
            String provider
    );

    List<Payment> findByOrder_IdAndStatusIn(UUID orderId, Collection<String> statuses);

    Optional<Payment> findFirstByOrder_IdAndStatusInOrderByCreatedAtDesc(
            UUID orderId,
            Collection<String> statuses
    );

    boolean existsByProviderAndProviderTransactionIdAndIdNot(
            String provider,
            String providerTransactionId,
            UUID id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);
}
