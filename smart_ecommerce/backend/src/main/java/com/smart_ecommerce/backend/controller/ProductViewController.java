package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.productview.ProductViewRequest;
import com.smart_ecommerce.backend.dto.productview.ProductViewResponse;
import com.smart_ecommerce.backend.service.ProductViewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ProductViewController {

    private final ProductViewService productViewService;

    @PostMapping("/products/{productId}/views")
    public ResponseEntity<ProductViewResponse> recordView(
            @PathVariable UUID productId,
            @Valid @RequestBody(required = false) ProductViewRequest request
    ) {
        return ResponseEntity.ok(
                productViewService.recordView(productId, request));
    }

    @GetMapping("/views/mine")
    public ResponseEntity<List<ProductViewResponse>> getMyHistory() {
        return ResponseEntity.ok(productViewService.getMyHistory());
    }

    @GetMapping("/views/mine/count")
    public ResponseEntity<Long> getMyViewCount() {
        return ResponseEntity.ok(productViewService.getMyViewCount());
    }

    @DeleteMapping("/views/mine")
    public ResponseEntity<Void> clearMyHistory() {
        productViewService.clearMyHistory();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products/{productId}/views/count")
    public ResponseEntity<Long> getProductViewCount(
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(productViewService.getProductViewCount(productId));
    }

    @GetMapping("/products/{productId}/views")
    public ResponseEntity<List<ProductViewResponse>> getProductViewsForAdmin(
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(
                productViewService.getProductViewsForAdmin(productId));
    }
}
