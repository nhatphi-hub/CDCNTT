package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.coupon.*;
import com.smart_ecommerce.backend.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    public ResponseEntity<CouponResponse> create(
            @Valid @RequestBody CouponRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(couponService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<CouponResponse>> getAll() {
        return ResponseEntity.ok(couponService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CouponResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(couponService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CouponResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody CouponRequest request) {
        return ResponseEntity.ok(couponService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        couponService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/validate")
    public ResponseEntity<CouponValidateResponse> validate(
            @Valid @RequestBody CouponValidateRequest request) {
        return ResponseEntity.ok(couponService.validate(request));
    }
}
