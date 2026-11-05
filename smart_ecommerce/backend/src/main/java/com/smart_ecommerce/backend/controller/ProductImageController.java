package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.productimage.ProductImageRequest;
import com.smart_ecommerce.backend.dto.productimage.ProductImageResponse;
import com.smart_ecommerce.backend.service.ProductImageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products/{productId}/images")
public class ProductImageController {

    private final ProductImageService imageService;

    public ProductImageController(ProductImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping
    public ResponseEntity<List<ProductImageResponse>> getByProduct(
            Authentication authentication,
            @PathVariable UUID productId
    ) {
        boolean admin = authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return ResponseEntity.ok(admin
                ? imageService.getAllByProduct(productId)
                : imageService.getByProduct(productId));
    }

    @PostMapping
    public ResponseEntity<ProductImageResponse> create(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductImageRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(imageService.create(productId, request));
    }

    @PutMapping("/items/{imageId}")
    public ResponseEntity<ProductImageResponse> update(
            @PathVariable UUID imageId,
            @Valid @RequestBody ProductImageRequest request
    ) {
        return ResponseEntity.ok(imageService.update(imageId, request));
    }

    @PutMapping("/items/{imageId}/primary")
    public ResponseEntity<ProductImageResponse> setPrimary(
            @PathVariable UUID imageId
    ) {
        return ResponseEntity.ok(imageService.setPrimary(imageId));
    }

    @DeleteMapping("/items/{imageId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID imageId
    ) {
        imageService.delete(imageId);
        return ResponseEntity.noContent().build();
    }
}
