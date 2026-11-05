package com.smart_ecommerce.backend.dto.coupon;

import java.math.BigDecimal;
import java.util.UUID;

public record CouponApplication(
        UUID couponId,
        String code,
        BigDecimal discountAmount
) {
}
