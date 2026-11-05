package com.smart_ecommerce.backend.controller;
import com.smart_ecommerce.backend.dto.seller.*;
import com.smart_ecommerce.backend.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/admin/shops") @RequiredArgsConstructor
public class AdminShopController {
    private final ShopService shops;
    @GetMapping("/pending") public List<ShopResponse> pending() { return shops.pending(); }
    @PutMapping("/{id}/status") public ShopResponse decide(@PathVariable UUID id,
        @Valid @RequestBody ShopDecisionRequest request) { return shops.decide(id, request.status()); }
}
