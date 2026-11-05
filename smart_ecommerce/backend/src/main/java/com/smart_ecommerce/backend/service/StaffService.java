package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.user.UserResponse;
import com.smart_ecommerce.backend.entity.Role;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.RoleRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<UserResponse> getAllStaff() {
        return userRepository.findAllStaff().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getStaff(UUID userId) {
        User user = findUser(userId);
        if (!hasRole(user, "STAFF")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên");
        }
        return toResponse(user);
    }

    @Transactional
    public UserResponse grantStaff(UUID userId) {
        User user = findUser(userId);
        if (hasRole(user, "ADMIN")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Không thể cấp STAFF cho tài khoản ADMIN");
        }
        if (hasRole(user, "STAFF")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Người dùng đã có quyền STAFF");
        }

        Role staffRole = roleRepository.findAll().stream()
                .filter(role -> "STAFF".equals(normalizeRoleName(role.getName())))
                .sorted((left, right) -> Boolean.compare(
                        !"STAFF".equalsIgnoreCase(left.getName()),
                        !"STAFF".equalsIgnoreCase(right.getName())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Role STAFF chưa được khởi tạo"));

        if (user.getRoles() == null) {
            user.setRoles(new HashSet<>());
        }
        user.getRoles().add(staffRole);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void revokeStaff(UUID userId) {
        User user = findUser(userId);
        if (user.getRoles() == null) {
            return;
        }

        // Only remove STAFF links. Other roles such as CUSTOMER and ADMIN remain untouched.
        user.getRoles().removeIf(role -> "STAFF".equals(normalizeRoleName(role.getName())));
        userRepository.save(user);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
    }

    private boolean hasRole(User user, String roleName) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> roleName.equals(normalizeRoleName(role.getName())));
    }

    private UserResponse toResponse(User user) {
        Set<String> roles = user.getRoles() == null
                ? Set.of()
                : user.getRoles().stream()
                        .map(Role::getName)
                        .map(StaffService::normalizeRoleName)
                        .collect(Collectors.toSet());

        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .roles(roles)
                .build();
    }

    private static String normalizeRoleName(String roleName) {
        String normalized = roleName == null
                ? ""
                : roleName.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_")
                ? normalized.substring("ROLE_".length())
                : normalized;
    }
}
