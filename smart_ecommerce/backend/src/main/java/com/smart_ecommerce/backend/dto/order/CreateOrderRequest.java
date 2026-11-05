package com.smart_ecommerce.backend.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;
import java.util.List;

public record CreateOrderRequest(

        @NotNull(message = "addressId không được để trống")
        UUID addressId,

        @NotBlank(message = "paymentMethod không được để trống")
        String paymentMethod,

        String note,

        @Size(max = 50, message = "couponCode không được vượt quá 50 ký tự")
        String couponCode,
        // Optional for backwards compatibility: null means checkout every cart line.
        List<UUID> selectedCartItemIds
) {
    public CreateOrderRequest(UUID addressId, String paymentMethod,
                              String note, String couponCode) {
        this(addressId, paymentMethod, note, couponCode, null);
    }
}
