package com.smart_ecommerce.backend.dto.coupon;

import com.smart_ecommerce.backend.entity.Coupon.DiscountType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponRequest(
        @NotBlank @Size(max = 50) String code,
        @Size(max = 255) String description,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal discountValue,
        @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal minOrderAmount,
        @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal maxDiscountAmount,
        @Min(1) Integer usageLimit,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        Boolean active
) {}
