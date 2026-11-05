package com.smart_ecommerce.backend.dto.user;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;

@Getter
@Builder
public class UserResponse {

    private UUID id;

    private String fullName;

    private String email;

    private String phone;

    private String avatar;

    private String status;

    private Set<String> roles;
}