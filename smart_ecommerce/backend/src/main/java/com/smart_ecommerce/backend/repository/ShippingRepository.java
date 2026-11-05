package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.Shipping;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShippingRepository extends JpaRepository<Shipping, UUID> {

    Optional<Shipping> findByOrder_Id(UUID orderId);

    List<Shipping> findAllByOrderByCreatedAtDesc();

    boolean existsByOrder_Id(UUID orderId);

    boolean existsByTrackingCodeIgnoreCase(String trackingCode);

    boolean existsByTrackingCodeIgnoreCaseAndIdNot(String trackingCode, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shipping s where s.id = :id")
    Optional<Shipping> findByIdForUpdate(@Param("id") UUID id);
}
