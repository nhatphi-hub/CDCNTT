package com.smart_ecommerce.backend.dto.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(

        @NotBlank(message = "Tên người nhận không được để trống")
        @Size(max = 150, message = "Tên người nhận tối đa 150 ký tự")
        String recipientName,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phone,

        @NotBlank(message = "Tỉnh/Thành phố không được để trống")
        @Size(max = 100, message = "Tỉnh/Thành phố tối đa 100 ký tự")
        String province,

        @NotBlank(message = "Quận/Huyện không được để trống")
        @Size(max = 100, message = "Quận/Huyện tối đa 100 ký tự")
        String district,

        @NotBlank(message = "Phường/Xã không được để trống")
        @Size(max = 100, message = "Phường/Xã tối đa 100 ký tự")
        String ward,

        @NotBlank(message = "Địa chỉ chi tiết không được để trống")
        @Size(max = 255, message = "Địa chỉ chi tiết tối đa 255 ký tự")
        String addressDetail,

        Boolean isDefault
) {
}