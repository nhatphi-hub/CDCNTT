package com.smart_ecommerce.backend.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCallbackRequest(
        @NotNull(message = "paymentId không được để trống")
        UUID paymentId,

        @NotBlank(message = "providerTransactionId không được để trống")
        @Size(max = 150, message = "providerTransactionId không được vượt quá 150 ký tự")
        String providerTransactionId,

        @NotNull(message = "amount không được để trống")
        @DecimalMin(value = "0.00", message = "amount phải lớn hơn hoặc bằng 0")
        @Digits(integer = 13, fraction = 2, message = "amount tối đa 2 chữ số thập phân")
        BigDecimal amount,

        @NotBlank(message = "currency không được để trống")
        @Pattern(regexp = "(?i)[A-Z]{3}", message = "currency phải là mã 3 ký tự")
        String currency,

        @NotBlank(message = "status không được để trống")
        @Pattern(regexp = "(?i)PAID|FAILED", message = "status chỉ được là PAID hoặc FAILED")
        String status
) {
}
