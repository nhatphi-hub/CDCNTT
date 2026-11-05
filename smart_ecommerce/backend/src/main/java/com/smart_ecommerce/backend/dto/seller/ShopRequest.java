package com.smart_ecommerce.backend.dto.seller;
import jakarta.validation.constraints.*;

public record ShopRequest(
    @NotBlank @Size(min = 3, max = 60) String name,
    @NotBlank @Size(max = 100) String primaryCategory,
    @Size(max = 220) String description,
    @NotBlank @Size(max = 150) String contactName,
    @NotBlank @Pattern(regexp = "[0-9+(). -]{9,20}") String contactPhone,
    @NotBlank @Email @Size(max = 150) String contactEmail,
    @NotBlank @Size(max = 500) String pickupAddress
) {}
