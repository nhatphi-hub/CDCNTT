package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.product.*;
import com.smart_ecommerce.backend.dto.productvariant.*;
import com.smart_ecommerce.backend.dto.seller.*;
import com.smart_ecommerce.backend.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/seller/products") @RequiredArgsConstructor
public class SellerProductController {
    private final SellerProductService service;
    private final UserService users;
    private UUID owner(Authentication a) { return users.getUserIdByEmail(a.getName()); }
    @GetMapping public List<ProductResponse> list(Authentication a) {return service.list(owner(a));}
    @GetMapping("/{id}") public ProductResponse one(Authentication a,@PathVariable UUID id) {return service.one(owner(a),id);}
    @PostMapping public ResponseEntity<ProductResponse> create(Authentication a,@Valid @RequestBody ProductRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(owner(a),r));
    }
    @PutMapping("/{id}") public ProductResponse update(Authentication a,@PathVariable UUID id,@Valid @RequestBody ProductRequest r) {
        return service.update(owner(a),id,r);
    }
    @PatchMapping("/{id}/visibility") public ProductResponse visibility(Authentication a,@PathVariable UUID id,@Valid @RequestBody SellerProductVisibility r) {
        return service.visibility(owner(a),id,r.visible());
    }
    // Removal from sale intentionally retains order history and product links.
    @DeleteMapping("/{id}") public ResponseEntity<Void> deleteProduct(Authentication a,@PathVariable UUID id) {
        service.removeProductFromSale(owner(a), id);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{productId}/variants") public List<ProductVariantResponse> variants(Authentication a,@PathVariable UUID productId) {
        return service.variants(owner(a),productId);
    }
    @PostMapping("/{productId}/variants") public ResponseEntity<ProductVariantResponse> variant(Authentication a,@PathVariable UUID productId,@Valid @RequestBody ProductVariantRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addVariant(owner(a),productId,r));
    }
    @PostMapping("/{productId}/variants/bulk")
    public ResponseEntity<List<ProductVariantResponse>> variantBulk(Authentication a,
           @PathVariable UUID productId, @Valid @RequestBody List<@Valid ProductVariantRequest> requests) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addVariantsBulk(owner(a), productId, requests));
    }
    @PutMapping("/{productId}/variants/{variantId}") public ProductVariantResponse variantUpdate(Authentication a,@PathVariable UUID productId,@PathVariable UUID variantId,@Valid @RequestBody ProductVariantRequest r) {
        return service.updateVariant(owner(a),productId,variantId,r);
    }
    @DeleteMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<Map<String, Boolean>> deleteVariant(Authentication a,@PathVariable UUID productId,@PathVariable UUID variantId) {
        boolean hardDeleted = service.deleteVariant(owner(a), productId, variantId);
        return ResponseEntity.ok(Map.of("hardDeleted", hardDeleted));
    }
    @PatchMapping("/{productId}/variants/{variantId}/stock") public ProductVariantResponse stock(Authentication a,@PathVariable UUID productId,@PathVariable UUID variantId,@Valid @RequestBody SellerStockRequest r) {
        return service.stock(owner(a),productId,variantId,r.quantity());
    }
}
