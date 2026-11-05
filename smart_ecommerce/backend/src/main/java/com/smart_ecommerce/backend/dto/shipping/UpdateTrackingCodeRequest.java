package com.smart_ecommerce.backend.dto.shipping;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTrackingCodeRequest(
        @NotBlank(message = "trackingCode không được để trống")
        @Size(max = 100, message = "trackingCode không được vượt quá 100 ký tự")
        String trackingCode
) {
}
