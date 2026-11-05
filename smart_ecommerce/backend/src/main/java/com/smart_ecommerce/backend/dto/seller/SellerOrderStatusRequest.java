package com.smart_ecommerce.backend.dto.seller;
import jakarta.validation.constraints.NotBlank;
public record SellerOrderStatusRequest(@NotBlank String status) {}
