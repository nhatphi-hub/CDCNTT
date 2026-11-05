package com.smart_ecommerce.backend.dto.productview;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ProductViewRequest(
        @Min(value = 0, message = "durationSeconds phải >= 0")
        @Max(value = 86400, message = "durationSeconds không được vượt quá 86400")
        Integer durationSeconds
) {}
