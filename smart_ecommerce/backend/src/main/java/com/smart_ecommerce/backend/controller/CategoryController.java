package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.category.CategoryRequest;
import com.smart_ecommerce.backend.dto.category.CategoryResponse;
import com.smart_ecommerce.backend.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import com.smart_ecommerce.backend.service.ProductMediaStorage;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final ProductMediaStorage mediaStorage;

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CategoryRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(categoryService.create(request));
    }

    // Only ADMIN may upload; SecurityConfig already protects POST /api/categories/**.
    // Shares the existing image storage/validation and public media serving.
    @PostMapping(value = "/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(@RequestPart("image") MultipartFile file) {
        return ResponseEntity.ok(Map.of("image", mediaStorage.store(file)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(
            @PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAll() {

        return ResponseEntity.ok(
                categoryService.getAll()
        );
    }

    @GetMapping("/root")
    public ResponseEntity<List<CategoryResponse>> getRootCategories() {

        return ResponseEntity.ok(
                categoryService.getRootCategories()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                categoryService.getById(id)
        );
    }

    @GetMapping("/{id}/children")
    public ResponseEntity<List<CategoryResponse>> getChildren(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                categoryService.getChildren(id)
        );
    }
}