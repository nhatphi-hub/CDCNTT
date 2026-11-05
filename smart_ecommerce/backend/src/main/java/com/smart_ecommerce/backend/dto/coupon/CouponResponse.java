package com.smart_ecommerce.backend.dto.coupon;

import com.smart_ecommerce.backend.entity.Coupon.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CouponResponse(
        UUID id,
        String code,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        Integer usageLimit,
        Integer usedCount,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Boolean active
) {}
