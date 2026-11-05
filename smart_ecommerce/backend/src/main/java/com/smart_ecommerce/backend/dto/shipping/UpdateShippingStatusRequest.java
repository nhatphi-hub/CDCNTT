package com.smart_ecommerce.backend.dto.shipping;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateShippingStatusRequest(
        @NotBlank(message = "shippingStatus không được để trống")
        @Pattern(
                regexp = "(?i)READY|IN_TRANSIT|DELIVERED|FAILED",
                message = "shippingStatus phải là READY, IN_TRANSIT, DELIVERED hoặc FAILED"
        )
        String shippingStatus
) {
}
