package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.productvariant.ProductVariantRequest;
import com.smart_ecommerce.backend.dto.productvariant.ProductVariantResponse;
import com.smart_ecommerce.backend.service.ProductVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/product-variants")
@RequiredArgsConstructor
public class ProductVariantController {

    private final ProductVariantService variantService;

    @PostMapping
    public ResponseEntity<ProductVariantResponse> create(
            @Valid @RequestBody ProductVariantRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(variantService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductVariantResponse> getById(
            Authentication authentication,
            @PathVariable UUID id
    ) {

        return ResponseEntity.ok(
                isCatalogManager(authentication)
                        ? variantService.getById(id)
                        : variantService.getActiveById(id)
        );
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<ProductVariantResponse>> getByProduct(
            Authentication authentication,
            @PathVariable UUID productId
    ) {

        return ResponseEntity.ok(
                isCatalogManager(authentication)
                        ? variantService.getAllByProduct(productId)
                        : variantService.getByProduct(productId)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductVariantResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProductVariantRequest request
    ) {

        return ResponseEntity.ok(
                variantService.update(id, request)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id
    ) {

        variantService.delete(id);

        return ResponseEntity.noContent().build();
    }

    private boolean isCatalogManager(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_STAFF".equals(authority.getAuthority()));
    }
}
