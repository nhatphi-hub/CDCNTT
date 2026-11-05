package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.product.ProductRequest;
import com.smart_ecommerce.backend.dto.product.ProductResponse;
import com.smart_ecommerce.backend.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(productService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAll() {
        return ResponseEntity.ok(productService.getAll());
    }

    @GetMapping("/page")
    public ResponseEntity<Page<ProductResponse>> getPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(
                productService.getPage(page, size)
        );
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(
                productService.search(keyword, page, size)
        );
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ProductResponse>> getByCategory(
            @PathVariable UUID categoryId
    ) {
        return ResponseEntity.ok(
                productService.getByCategory(categoryId)
        );
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<ProductResponse>> getByStatus(
            @PathVariable String status
    ) {
        return ResponseEntity.ok(
                productService.getByStatus(status)
        );
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ProductResponse> getBySlug(
            @PathVariable String slug
    ) {
        return ResponseEntity.ok(
                productService.getBySlug(slug)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(
            Authentication authentication,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                isCatalogManager(authentication)
                        ? productService.getById(id)
                        : productService.getActiveById(id)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request
    ) {
        return ResponseEntity.ok(
                productService.update(id, request)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id
    ) {
        productService.delete(id);

        return ResponseEntity.noContent().build();
    }

    private boolean isCatalogManager(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_STAFF".equals(authority.getAuthority()));
    }
}
