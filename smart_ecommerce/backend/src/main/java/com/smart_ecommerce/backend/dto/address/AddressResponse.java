package com.smart_ecommerce.backend.dto.address;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class AddressResponse {

    private UUID id;

    private UUID userId;

    private String recipientName;

    private String phone;

    private String province;

    private String district;

    private String ward;

    private String addressDetail;

    private Boolean isDefault;

    private LocalDateTime createdAt;
}