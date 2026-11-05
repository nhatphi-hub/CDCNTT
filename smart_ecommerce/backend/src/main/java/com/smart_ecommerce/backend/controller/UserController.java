package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.user.GoogleTokenRequest;
import com.smart_ecommerce.backend.dto.user.LoginRequest;
import com.smart_ecommerce.backend.dto.user.LoginResponse;
import com.smart_ecommerce.backend.dto.user.RegisterRequest;
import com.smart_ecommerce.backend.dto.user.UserResponse;
import com.smart_ecommerce.backend.service.GoogleAuthService;
import com.smart_ecommerce.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final GoogleAuthService googleAuthService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        UserResponse response = userService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    @PostMapping("/google")
    public ResponseEntity<LoginResponse> loginGoogle(@Valid @RequestBody GoogleTokenRequest request) {
        return ResponseEntity.ok(googleAuthService.login(request.idToken()));
    }

    @GetMapping("/google/link")
    public Map<String, Boolean> googleLinkStatus(Authentication authentication) {
        return Map.of("linked", googleAuthService.isLinked(authentication.getName()));
    }

    @PostMapping("/google/link")
    public Map<String, Boolean> linkGoogle(
            @Valid @RequestBody GoogleTokenRequest request,
            Authentication authentication) {
        googleAuthService.link(authentication.getName(), request.idToken());
        return Map.of("linked", true);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        LoginResponse response = userService.login(request);

        return ResponseEntity.ok(response);
    }
}