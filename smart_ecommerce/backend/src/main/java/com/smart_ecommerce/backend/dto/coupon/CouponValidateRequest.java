package com.smart_ecommerce.backend.dto.coupon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record CouponValidateRequest(
        @NotBlank String code,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal orderAmount
) {}
