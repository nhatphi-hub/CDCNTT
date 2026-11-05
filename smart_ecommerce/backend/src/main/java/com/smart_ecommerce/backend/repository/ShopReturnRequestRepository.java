package com.smart_ecommerce.backend.repository;
import com.smart_ecommerce.backend.entity.ShopReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ShopReturnRequestRepository extends JpaRepository<ShopReturnRequest, UUID> {
    List<ShopReturnRequest> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<ShopReturnRequest> findByShopIdOrderByCreatedAtDesc(UUID shopId);
    Optional<ShopReturnRequest> findByOrderIdAndShopId(UUID orderId, UUID shopId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ShopReturnRequest r where r.id = :id")
    Optional<ShopReturnRequest> lock(@Param("id") UUID id);
}
