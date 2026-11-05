package com.smart_ecommerce.backend.controller;

import com.smart_ecommerce.backend.dto.user.UserResponse;
import com.smart_ecommerce.backend.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllStaff() {
        return ResponseEntity.ok(staffService.getAllStaff());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getStaff(@PathVariable UUID userId) {
        return ResponseEntity.ok(staffService.getStaff(userId));
    }

    @PostMapping("/{userId}")
    public ResponseEntity<UserResponse> grantStaff(@PathVariable UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(staffService.grantStaff(userId));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> revokeStaff(@PathVariable UUID userId) {
        staffService.revokeStaff(userId);
        return ResponseEntity.noContent().build();
    }
}
