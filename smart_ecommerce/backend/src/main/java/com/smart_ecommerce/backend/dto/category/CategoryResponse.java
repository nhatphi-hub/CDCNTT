package com.smart_ecommerce.backend.dto.category;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CategoryResponse {

    private UUID id;

    private UUID parentId;

    private String name;

    private String slug;

    private String description;

    private String image;

    private Boolean isActive;
}