package com.smart_ecommerce.backend.dto.order;

import jakarta.validation.constraints.NotBlank;

public record UpdateOrderStatusRequest(

        @NotBlank(message = "status không được để trống")
        String status
) {
}
