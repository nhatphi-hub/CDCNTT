package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.user.LoginRequest;
import com.smart_ecommerce.backend.dto.user.LoginResponse;
import com.smart_ecommerce.backend.dto.user.RegisterRequest;
import com.smart_ecommerce.backend.dto.user.UserResponse;
import com.smart_ecommerce.backend.entity.Role;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.RoleRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mật khẩu không được vượt quá 72 byte UTF-8 khi dùng BCrypt");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Email đã được sử dụng");
        }

        Role customerRole = roleRepository.findByNameIgnoreCase("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException(
                        "Role CUSTOMER chưa được khởi tạo"));

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .status("ACTIVE")
                .roles(Set.of(customerRole))
                .build();

        return convertToResponse(userRepository.save(user));
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(
                        normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Email hoặc mật khẩu không đúng"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Email hoặc mật khẩu không đúng");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa");
        }

        return createLoginResponse(user);
    }

    // Shared response format for password and Google login.
    LoginResponse createLoginResponse(User user) {
        String token = jwtService.generateToken(user);
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .map(UserService::normalizeRoleName)
                .collect(Collectors.toSet());

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .roles(roles)
                .build();
    }

    @Transactional(readOnly = true)
    public UUID getUserIdByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Không tìm thấy người dùng"))
                .getId();
    }

    private UserResponse convertToResponse(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .map(UserService::normalizeRoleName)
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

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
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
