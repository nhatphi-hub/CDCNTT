package com.smart_ecommerce.backend.dto.coupon;

import java.math.BigDecimal;

public record CouponValidateResponse(
        boolean valid,
        String code,
        String message,
        BigDecimal discountAmount,
        BigDecimal finalAmount
) {}
