package com.smart_ecommerce.backend.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull(message = "orderId không được để trống")
        UUID orderId,

        @NotBlank(message = "paymentMethod không được để trống")
        @Pattern(
                regexp = "(?i)COD|BANK_TRANSFER|VNPAY|MOMO",
                message = "paymentMethod phải là COD, BANK_TRANSFER, VNPAY hoặc MOMO"
        )
        String paymentMethod
) {
}
