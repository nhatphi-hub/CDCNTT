package com.smart_ecommerce.backend.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleTokenRequest(
        @NotBlank(message = "Google ID Token không được để trống")
        @Size(max = 16384, message = "Google ID Token quá dài")
        String idToken
) { }
