package com.smart_ecommerce.backend.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(

        @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 200, message = "Tên sản phẩm tối đa 200 ký tự")
        String name,

        @NotBlank(message = "Slug không được để trống")
        @Size(max = 220, message = "Slug tối đa 220 ký tự")
        String slug,

        @NotBlank(message = "SKU không được để trống")
        @Size(max = 100, message = "SKU tối đa 100 ký tự")
        String sku,

        @Size(max = 100, message = "Brand tối đa 100 ký tự")
        String brand,

        String description,

        @NotNull(message = "Giá sản phẩm không được để trống")
        @DecimalMin(value = "0.0", message = "Giá sản phẩm phải >= 0")
        @Digits(integer = 13, fraction = 2, message = "Giá sản phẩm tối đa 2 chữ số thập phân")
        BigDecimal price,

        @DecimalMin(value = "0.0", message = "Giá khuyến mãi phải >= 0")
        @Digits(integer = 13, fraction = 2, message = "Giá khuyến mãi tối đa 2 chữ số thập phân")
        BigDecimal salePrice,

        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 0, message = "Số lượng phải >= 0")
        Integer quantity,

        @Size(max = 20, message = "Gender tối đa 20 ký tự")
        String gender,

        @Size(max = 50, message = "Product type tối đa 50 ký tự")
        String productType,

        @Size(max = 20, message = "Status tối đa 20 ký tự")
        String status,

        UUID categoryId
) {
}
