package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.payment.CreatePaymentRequest;
import com.smart_ecommerce.backend.dto.payment.PaymentCallbackRequest;
import com.smart_ecommerce.backend.dto.payment.PaymentResponse;
import com.smart_ecommerce.backend.service.PaymentService;
import com.smart_ecommerce.backend.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key")
            @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                paymentService.createPayment(
                        getUserId(authentication), request, idempotencyKey));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> get(
            Authentication authentication,
            @PathVariable UUID paymentId
    ) {
        boolean admin = isAdmin(authentication);
        return ResponseEntity.ok(paymentService.getPayment(
                getUserId(authentication), paymentId, admin));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<PaymentResponse>> getByOrder(
            Authentication authentication,
            @PathVariable UUID orderId
    ) {
        return ResponseEntity.ok(paymentService.getPaymentsByOrder(
                orderId, getUserId(authentication), isAdmin(authentication)));
    }

    @PostMapping("/callback/{provider}")
    public ResponseEntity<PaymentResponse> callback(
            @PathVariable String provider,
            @RequestHeader(name = "X-Payment-Signature", required = false) String signature,
            @Valid @RequestBody PaymentCallbackRequest request
    ) {
        return ResponseEntity.ok(paymentService.handleCallback(provider, request, signature));
    }

    private UUID getUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        return userService.getUserIdByEmail(authentication.getName());
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
