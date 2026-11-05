package com.smart_ecommerce.backend.dto.seller;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
public record SellerStockRequest(@NotNull @Min(0) Integer quantity) {}
