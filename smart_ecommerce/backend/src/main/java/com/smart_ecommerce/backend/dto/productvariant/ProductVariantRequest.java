package com.smart_ecommerce.backend.dto.productvariant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariantRequest(

        @NotNull(message = "Product ID không được để trống")
        UUID productId,

        @Size(
                max = 30,
                message = "Size tối đa 30 ký tự"
        )
        String size,

        @Size(
                max = 50,
                message = "Color tối đa 50 ký tự"
        )
        String color,

        @NotBlank(
                message = "SKU không được để trống"
        )
        @Size(
                max = 100,
                message = "SKU tối đa 100 ký tự"
        )
        String sku,

        @DecimalMin(
                value = "0.0",
                message = "Giá phải >= 0"
        )
        @Digits(integer = 13, fraction = 2, message = "Giá tối đa 2 chữ số thập phân")
        BigDecimal price,

        @DecimalMin(
                value = "0.0",
                message = "Giá khuyến mãi phải >= 0"
        )
        @Digits(integer = 13, fraction = 2, message = "Giá khuyến mãi tối đa 2 chữ số thập phân")
        BigDecimal salePrice,

        @NotNull(
                message = "Số lượng không được để trống"
        )
        @Min(
                value = 0,
                message = "Số lượng phải >= 0"
        )
        Integer quantity,

        @Size(
                max = 50,
                message = "Status tối đa 50 ký tự"
        )
        String status,

        // Optional: legacy variants remain valid without a photo association.
        UUID imageId
) {
    // Keep Java clients and existing unit tests using the previous constructor.
    public ProductVariantRequest(UUID productId, String size, String color, String sku,
                                 BigDecimal price, BigDecimal salePrice, Integer quantity, String status) {
        this(productId, size, color, sku, price, salePrice, quantity, status, null);
    }
}
