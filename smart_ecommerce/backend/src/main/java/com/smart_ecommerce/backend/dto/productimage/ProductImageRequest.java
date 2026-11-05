package com.smart_ecommerce.backend.dto.productimage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductImageRequest(
        @NotBlank(message = "imageUrl không được để trống")
        @Size(max = 2000, message = "imageUrl tối đa 2000 ký tự")
        String imageUrl,

        Boolean isPrimary,

        @Min(value = 0, message = "sortOrder phải >= 0")
        Integer sortOrder
) {}
