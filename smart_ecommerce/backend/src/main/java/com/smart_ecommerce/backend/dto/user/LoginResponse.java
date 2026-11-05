package com.smart_ecommerce.backend.dto.user;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;

@Getter
@Builder
public class LoginResponse {

    private String accessToken;

    private String tokenType;

    private UUID userId;

    private String fullName;

    private String email;

    private Set<String> roles;
}