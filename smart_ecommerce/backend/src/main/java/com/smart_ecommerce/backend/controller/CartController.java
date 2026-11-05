package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.cart.AddCartItemRequest;
import com.smart_ecommerce.backend.dto.cart.CartResponse;
import com.smart_ecommerce.backend.dto.cart.UpdateCartItemRequest;
import com.smart_ecommerce.backend.service.CartService;
import com.smart_ecommerce.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(
            Authentication authentication
    ) {

        UUID userId = getUserId(authentication);

        return ResponseEntity.ok(
                cartService.getCart(userId)
        );
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            Authentication authentication,
            @Valid @RequestBody AddCartItemRequest request
    ) {

        UUID userId = getUserId(authentication);

        return ResponseEntity.ok(
                cartService.addItem(
                        userId,
                        request
                )
        );
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> updateItem(
            Authentication authentication,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {

        UUID userId = getUserId(authentication);

        return ResponseEntity.ok(
                cartService.updateItem(
                        userId,
                        itemId,
                        request
                )
        );
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> removeItem(
            Authentication authentication,
            @PathVariable UUID itemId
    ) {

        UUID userId = getUserId(authentication);

        return ResponseEntity.ok(
                cartService.removeItem(
                        userId,
                        itemId
                )
        );
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(
            Authentication authentication
    ) {

        UUID userId = getUserId(authentication);

        cartService.clearCart(userId);

        return ResponseEntity.noContent().build();
    }

    private UUID getUserId(
            Authentication authentication
    ) {

        if (authentication == null
                || authentication.getName() == null) {

            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }

        return userService
                .getUserIdByEmail(
                        authentication.getName()
                );
    }
}
