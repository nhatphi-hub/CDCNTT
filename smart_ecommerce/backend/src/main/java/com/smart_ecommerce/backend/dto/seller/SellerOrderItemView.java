package com.smart_ecommerce.backend.dto.seller;
import java.math.BigDecimal;
import java.util.UUID;
public record SellerOrderItemView(UUID id, UUID productId, String productName, String sku,
    String variantName, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {}
