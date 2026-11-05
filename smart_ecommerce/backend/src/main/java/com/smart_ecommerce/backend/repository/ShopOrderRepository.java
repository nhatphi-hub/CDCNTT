package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.ShopOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ShopOrderRepository extends JpaRepository<ShopOrder, UUID> {
    Page<ShopOrder> findByShop_Id(UUID shopId, Pageable pageable);
    Page<ShopOrder> findByShop_IdAndStatus(UUID shopId, String status, Pageable pageable);
    Optional<ShopOrder> findByOrder_IdAndShop_Id(UUID orderId, UUID shopId);
    List<ShopOrder> findByOrder_Id(UUID orderId);
    List<ShopOrder> findByShop_Id(UUID shopId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select so from ShopOrder so where so.order.id = :orderId and so.shop.id = :shopId")
    Optional<ShopOrder> lockOwned(@Param("orderId") UUID orderId, @Param("shopId") UUID shopId);
}
