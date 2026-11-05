package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.order.CreateOrderRequest;
import com.smart_ecommerce.backend.dto.order.OrderResponse;
import com.smart_ecommerce.backend.service.OrderService;
import com.smart_ecommerce.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateOrderRequest request
    ) {

        UUID userId = getUserId(authentication);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(orderService.create(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getMyOrders(
            Authentication authentication
    ) {

        UUID userId = getUserId(authentication);

        return ResponseEntity.ok(
                orderService.getMyOrders(userId)
        );
    }

    @GetMapping("/management")
    public ResponseEntity<List<OrderResponse>> getOrdersForManagement() {
        return ResponseEntity.ok(orderService.getOrdersForManagement());
    }

    @GetMapping("/management/{id}")
    public ResponseEntity<OrderResponse> getOrderForManagement(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrderForManagement(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getMyOrder(
            Authentication authentication,
            @PathVariable UUID id
    ) {

        UUID userId = getUserId(authentication);

        return ResponseEntity.ok(
                orderService.getMyOrder(userId, id)
        );
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            Authentication authentication,
            @PathVariable UUID id
    ) {

        UUID userId = getUserId(authentication);

        return ResponseEntity.ok(
                orderService.cancel(userId, id)
        );
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody
                    com.smart_ecommerce.backend.dto.order.UpdateOrderStatusRequest request
    ) {

        return ResponseEntity.ok(
                orderService.updateStatus(
                        id,
                        request.status()
                )
        );
    }

    private UUID getUserId(Authentication authentication) {

        if (authentication == null
                || authentication.getName() == null) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }

        return userService.getUserIdByEmail(
                authentication.getName()
        );
    }
}
