package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.category.CategoryRequest;
import com.smart_ecommerce.backend.dto.category.CategoryResponse;
import com.smart_ecommerce.backend.entity.Category;
import com.smart_ecommerce.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryResponse create(CategoryRequest request) {

        if (categoryRepository.existsBySlug(request.getSlug())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Slug danh mục đã tồn tại");
        }

        Category parent = null;

        if (request.getParentId() != null) {
            parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "Danh mục cha không tồn tại"));
        }

        String image = validateImage(request.getImage());

        Category category = Category.builder()
                .parent(parent)
                .name(request.getName())
                .slug(request.getSlug())
                .description(request.getDescription())
                .image(image)
                .isActive(
                        request.getIsActive() != null
                                ? request.getIsActive()
                                : true
                )
                .build();

        category = categoryRepository.save(category);

        return convertToResponse(category);
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Danh mục không tồn tại"));
        categoryRepository.findBySlug(request.getSlug()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug danh mục đã tồn tại");
            }
        });
        Category parent = null;
        if (request.getParentId() != null) {
            parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Danh mục cha không tồn tại"));
            // Reject self-parenting and any cycle in the category tree.
            Category cursor = parent;
            while (cursor != null) {
                if (cursor.getId().equals(id)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Không thể chuyển danh mục vào chính nó hoặc danh mục con");
                }
                cursor = cursor.getParent();
            }
        }
        category.setParent(parent);
        category.setName(request.getName().trim());
        category.setSlug(request.getSlug().trim());
        category.setDescription(request.getDescription());
        category.setImage(validateImage(request.getImage()));
        category.setIsActive(request.getIsActive() == null || request.getIsActive());
        return convertToResponse(categoryRepository.save(category));
    }

    private String validateImage(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.startsWith("icon:")) {
            if (trimmed.matches("icon:(fashion|shoes|beauty|electronics|home|sport|books|food|accessories|kids|general)")) {
                return trimmed;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Icon danh mục không hợp lệ");
        }
        if (trimmed.matches("/api/media/products/[a-fA-F0-9-]{36}\\.(jpg|png|webp)")) return trimmed;
        try {
            java.net.URI uri = java.net.URI.create(trimmed);
            String scheme = uri.getScheme();
            if (("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && uri.getUserInfo() == null) return trimmed;
        } catch (IllegalArgumentException ignored) { }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ảnh phải là URL http(s), icon hợp lệ hoặc ảnh tải lên từ PPee");
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {

        return categoryRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getRootCategories() {

        return categoryRepository.findByParentIsNull()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getChildren(UUID parentId) {

        return categoryRepository.findByParentId(parentId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(UUID id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Danh mục không tồn tại"));

        return convertToResponse(category);
    }

    private CategoryResponse convertToResponse(Category category) {

        return CategoryResponse.builder()
                .id(category.getId())
                .parentId(
                        category.getParent() != null
                                ? category.getParent().getId()
                                : null
                )
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .image(category.getImage())
                .isActive(category.getIsActive())
                .build();
    }
}
