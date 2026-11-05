package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.coupon.CouponRequest;
import com.smart_ecommerce.backend.dto.coupon.CouponResponse;
import com.smart_ecommerce.backend.service.SellerCouponService;
import com.smart_ecommerce.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/seller/coupons") @RequiredArgsConstructor
public class SellerCouponController {
    private final SellerCouponService coupons; private final UserService users;
    @GetMapping public List<CouponResponse> mine(Authentication auth) {
        return coupons.mine(users.getUserIdByEmail(auth.getName()));
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse create(Authentication auth,@Valid @RequestBody CouponRequest request) {
        return coupons.create(users.getUserIdByEmail(auth.getName()), request);
    }
    @PutMapping("/{id}")
    public CouponResponse update(Authentication auth, @PathVariable UUID id,
                                 @Valid @RequestBody CouponRequest request) {
        return coupons.update(users.getUserIdByEmail(auth.getName()), id, request);
    }
    @PatchMapping("/{id}/active")
    public CouponResponse active(Authentication auth,@PathVariable UUID id,@RequestBody Map<String,Boolean> body) {
        if (!body.containsKey("active") || body.get("active") == null)
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu active");
        return coupons.setActive(users.getUserIdByEmail(auth.getName()),id,body.get("active"));
    }
}
