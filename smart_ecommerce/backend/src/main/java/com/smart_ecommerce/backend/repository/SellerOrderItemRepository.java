package com.smart_ecommerce.backend.repository;

import com.smart_ecommerce.backend.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SellerOrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrder_IdAndShop_Id(UUID orderId, UUID shopId);
}
