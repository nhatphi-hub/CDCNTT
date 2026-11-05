package com.smart_ecommerce.backend.controller;
import com.smart_ecommerce.backend.dto.seller.*;
import com.smart_ecommerce.backend.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/seller/shop") @RequiredArgsConstructor
public class SellerShopController {
    private final ShopService shops;
    private final UserService users;
    private UUID userId(Authentication authentication) { return users.getUserIdByEmail(authentication.getName()); }
    @PostMapping public ResponseEntity<ShopResponse> apply(Authentication a, @Valid @RequestBody ShopRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shops.apply(userId(a), request));
    }
    @GetMapping public ShopResponse mine(Authentication a) { return shops.mine(userId(a)); }
    @PutMapping public ShopResponse update(Authentication a, @Valid @RequestBody ShopRequest request) {
        return shops.updateMine(userId(a), request);
    }
}
