package com.smart_ecommerce.backend.dto.seller;
import jakarta.validation.constraints.NotNull;
public record SellerProductVisibility(@NotNull Boolean visible) {}
