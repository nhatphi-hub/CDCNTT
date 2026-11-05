package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.shipping.CreateShippingRequest;
import com.smart_ecommerce.backend.dto.shipping.ShippingResponse;
import com.smart_ecommerce.backend.dto.shipping.UpdateShippingStatusRequest;
import com.smart_ecommerce.backend.dto.shipping.UpdateTrackingCodeRequest;
import com.smart_ecommerce.backend.service.ShippingService;
import com.smart_ecommerce.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;
    private final UserService userService;

    @PostMapping("/orders/{orderId}")
    public ResponseEntity<ShippingResponse> create(
            @PathVariable UUID orderId,
            @Valid @RequestBody(required = false) CreateShippingRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shippingService.create(orderId, request));
    }

    @GetMapping
    public ResponseEntity<List<ShippingResponse>> getAllForManagement() {
        return ResponseEntity.ok(shippingService.getAllForManagement());
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ShippingResponse> getByOrder(
            Authentication authentication,
            @PathVariable UUID orderId
    ) {
        boolean manager = isManager(authentication);
        UUID userId = manager ? null : getUserId(authentication);
        return ResponseEntity.ok(shippingService.getByOrder(orderId, userId, manager));
    }

    @GetMapping("/{shippingId}")
    public ResponseEntity<ShippingResponse> getById(
            Authentication authentication,
            @PathVariable UUID shippingId
    ) {
        boolean manager = isManager(authentication);
        UUID userId = manager ? null : getUserId(authentication);
        return ResponseEntity.ok(shippingService.getById(shippingId, userId, manager));
    }

    @PatchMapping("/{shippingId}/status")
    public ResponseEntity<ShippingResponse> updateStatus(
            @PathVariable UUID shippingId,
            @Valid @RequestBody UpdateShippingStatusRequest request
    ) {
        return ResponseEntity.ok(
                shippingService.updateStatus(shippingId, request.shippingStatus()));
    }

    @PatchMapping("/{shippingId}/tracking-code")
    public ResponseEntity<ShippingResponse> updateTrackingCode(
            @PathVariable UUID shippingId,
            @Valid @RequestBody UpdateTrackingCodeRequest request
    ) {
        return ResponseEntity.ok(
                shippingService.updateTrackingCode(shippingId, request.trackingCode()));
    }

    private UUID getUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        return userService.getUserIdByEmail(authentication.getName());
    }

    private boolean isManager(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_ADMIN")
                        || authority.equals("ROLE_STAFF"));
    }
}
