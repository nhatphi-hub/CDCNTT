package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.address.AddressRequest;
import com.smart_ecommerce.backend.dto.address.AddressResponse;
import com.smart_ecommerce.backend.service.AddressService;
import com.smart_ecommerce.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;
    private final UserService userService;

    /**
     * Tạo địa chỉ
     */
    @PostMapping
    public ResponseEntity<AddressResponse> create(
            Authentication authentication,
            @Valid @RequestBody AddressRequest request
    ) {

        UUID userId = getUserId(authentication);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        addressService.create(
                                userId,
                                request
                        )
                );
    }

    /**
     * Lấy danh sách địa chỉ của User hiện tại
     */
    @GetMapping
    public ResponseEntity<List<AddressResponse>> getMyAddresses(
            Authentication authentication
    ) {

        UUID userId = getUserId(authentication);

        return ResponseEntity.ok(
                addressService.getMyAddresses(userId)
        );
    }

    /**
     * Lấy một địa chỉ
     */
    @GetMapping("/{id}")
    public ResponseEntity<AddressResponse> getById(
            Authentication authentication,
            @PathVariable UUID id
    ) {

        UUID userId = getUserId(authentication);

        return ResponseEntity.ok(
                addressService.getById(
                        userId,
                        id
                )
        );
    }

    /**
     * Cập nhật địa chỉ
     */
    @PutMapping("/{id}")
    public ResponseEntity<AddressResponse> update(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody AddressRequest request
    ) {

        UUID userId = getUserId(authentication);

        return ResponseEntity.ok(
                addressService.update(
                        userId,
                        id,
                        request
                )
        );
    }

    /**
     * Đặt địa chỉ mặc định
     */
    @PutMapping("/{id}/default")
    public ResponseEntity<AddressResponse> setDefault(
            Authentication authentication,
            @PathVariable UUID id
    ) {

        UUID userId = getUserId(authentication);

        return ResponseEntity.ok(
                addressService.setDefault(
                        userId,
                        id
                )
        );
    }

    /**
     * Xóa địa chỉ
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable UUID id
    ) {

        UUID userId = getUserId(authentication);

        addressService.delete(
                userId,
                id
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * Lấy ID User từ email trong JWT Authentication
     */
    private UUID getUserId(
            Authentication authentication
    ) {

        if (authentication == null
                || authentication.getName() == null) {

            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }

        return userService
                .getUserIdByEmail(
                        authentication.getName()
                );
    }
}
