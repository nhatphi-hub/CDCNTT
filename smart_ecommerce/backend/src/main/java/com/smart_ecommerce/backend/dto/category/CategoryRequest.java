package com.smart_ecommerce.backend.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CategoryRequest {

    private UUID parentId;

    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 100, message = "Tên danh mục không được vượt quá 100 ký tự")
    private String name;

    @NotBlank(message = "Slug không được để trống")
    @Size(max = 120, message = "Slug không được vượt quá 120 ký tự")
    private String slug;

    @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
    private String description;

    @Size(max = 255, message = "Đường dẫn hình ảnh không được vượt quá 255 ký tự")
    private String image;

    private Boolean isActive = true;
}
