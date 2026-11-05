package com.smart_ecommerce.backend.dto.order;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class OrderItemResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private String productSlug;
    private UUID variantId;
    private String variantName;
    private String sku;
    private String size;
    private String color;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String productImage;
    private String shopName;
}
