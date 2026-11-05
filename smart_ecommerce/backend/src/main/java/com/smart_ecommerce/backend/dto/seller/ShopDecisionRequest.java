package com.smart_ecommerce.backend.dto.seller;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public record ShopDecisionRequest(@NotBlank @Pattern(regexp = "ACTIVE|REJECTED|SUSPENDED") String status) {}
