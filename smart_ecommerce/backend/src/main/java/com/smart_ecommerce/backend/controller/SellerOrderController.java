package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.seller.*;
import com.smart_ecommerce.backend.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/seller/orders") @RequiredArgsConstructor
public class SellerOrderController {
    private final SellerOrderService orders;
    private final UserService users;
    private UUID userId(Authentication a) { return users.getUserIdByEmail(a.getName()); }
    @GetMapping public Page<SellerOrderView> list(Authentication a,
        @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return orders.list(userId(a), status, page, size);
    }
    @GetMapping("/analytics")
    public SellerAnalytics analytics(Authentication a, @RequestParam(defaultValue = "30") int days) {
        return orders.analytics(userId(a), days);
    }
    @GetMapping("/stats") public SellerOrderStats stats(Authentication a) { return orders.stats(userId(a)); }
    @GetMapping("/{id}") public SellerOrderView get(Authentication a, @PathVariable UUID id) {
        return orders.get(userId(a), id);
    }
    @PatchMapping("/{id}/status") public SellerOrderView status(Authentication a,
        @PathVariable UUID id, @Valid @RequestBody SellerOrderStatusRequest request) {
        return orders.transition(userId(a), id, request.status());
    }
}
