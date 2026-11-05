package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.favorite.FavoriteResponse;
import com.smart_ecommerce.backend.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/{productId}")
    public ResponseEntity<?> add(
            Authentication authentication,
            @PathVariable UUID productId
    ) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(favoriteService.add(authentication, productId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<FavoriteResponse>> getMyFavorites(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                favoriteService.getMyFavorites(authentication)
        );
    }

    @GetMapping("/check/{productId}")
    public ResponseEntity<Map<String, Object>> check(
            Authentication authentication,
            @PathVariable UUID productId
    ) {
        boolean favorite = favoriteService.isFavorite(
                authentication,
                productId
        );

        return ResponseEntity.ok(
                Map.of(
                        "productId", productId,
                        "favorite", favorite
                )
        );
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<?> remove(
            Authentication authentication,
            @PathVariable UUID productId
    ) {
        try {
            favoriteService.remove(authentication, productId);

            return ResponseEntity.ok(
                    Map.of("message", "Đã xóa sản phẩm khỏi yêu thích")
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
