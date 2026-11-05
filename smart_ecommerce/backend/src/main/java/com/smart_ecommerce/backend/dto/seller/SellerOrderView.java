package com.smart_ecommerce.backend.dto.seller;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
public record SellerOrderView(UUID orderId, String orderCode, String status, String orderStatus,
    String paymentStatus, String paymentMethod, String recipientName, String recipientPhone,
    String address, BigDecimal itemsSubtotal, BigDecimal allocatedDiscount,
    BigDecimal netItemsAmount, LocalDateTime createdAt, LocalDateTime updatedAt,
    List<SellerOrderItemView> items) {}
