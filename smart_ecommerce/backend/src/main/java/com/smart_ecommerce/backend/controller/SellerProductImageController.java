package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.productimage.ProductImageRequest;
import com.smart_ecommerce.backend.dto.productimage.ProductImageResponse;
import com.smart_ecommerce.backend.service.SellerProductImageService;
import com.smart_ecommerce.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** SecurityConfig requires ROLE_SELLER on /api/seller/products/**. */
@RestController
@RequestMapping("/api/seller/products/{productId}/images")
public class SellerProductImageController {
    private final SellerProductImageService images;
    private final UserService users;

    public SellerProductImageController(SellerProductImageService images, UserService users) {
        this.images = images;
        this.users = users;
    }

    private UUID owner(Authentication authentication) {
        return users.getUserIdByEmail(authentication.getName());
    }

    @GetMapping
    public List<ProductImageResponse> list(Authentication auth, @PathVariable UUID productId) {
        return images.list(owner(auth), productId);
    }

    @PostMapping
    public ResponseEntity<ProductImageResponse> add(Authentication auth, @PathVariable UUID productId,
                                                     @Valid @RequestBody ProductImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(images.add(owner(auth), productId, request));
    }

    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImageResponse> upload(Authentication auth, @PathVariable UUID productId,
                  @RequestPart("image") org.springframework.web.multipart.MultipartFile file,
                  @RequestParam(defaultValue = "false") boolean primary) {
        return ResponseEntity.status(HttpStatus.CREATED).body(images.upload(owner(auth),productId,file,primary));
    }

    @PutMapping("/{imageId}")
    public ProductImageResponse edit(Authentication auth, @PathVariable UUID productId,
                                    @PathVariable UUID imageId, @Valid @RequestBody ProductImageRequest request) {
        return images.edit(owner(auth), productId, imageId, request);
    }

    @PutMapping("/{imageId}/primary")
    public ProductImageResponse primary(Authentication auth, @PathVariable UUID productId,
                                       @PathVariable UUID imageId) {
        return images.makePrimary(owner(auth), productId, imageId);
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID productId,
                                      @PathVariable UUID imageId) {
        images.remove(owner(auth), productId, imageId);
        return ResponseEntity.noContent().build();
    }
}
