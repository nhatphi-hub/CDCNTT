package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.user.LoginResponse;
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

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {
    private final GoogleIdTokenService tokenService;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;

    @Transactional
    public LoginResponse login(String idToken) {
        GoogleIdTokenService.GoogleIdentity identity = tokenService.verify(idToken);
        User user = users.findByGoogleSub(identity.sub()).orElse(null);
        if (user == null) {
            // Explicit linking prevents taking over an existing password-based account.
            if (users.existsByEmailIgnoreCase(identity.email())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Email đã có tài khoản PPee. Hãy đăng nhập bằng mật khẩu, vào Cài đặt tài khoản và liên kết Google.");
            }
            Role customer = roles.findByNameIgnoreCase("CUSTOMER")
                    .orElseThrow(() -> new IllegalStateException("Role CUSTOMER chưa được khởi tạo"));
            byte[] passwordBytes = new byte[48];
            new SecureRandom().nextBytes(passwordBytes);
            String unusableRandomPassword = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(passwordBytes);
            // The existing database requires a non-null BCrypt hash. No one knows this password.
            user = User.builder()
                    .email(identity.email())
                    .fullName(identity.name())
                    .avatar(identity.picture())
                    .password(passwordEncoder.encode(unusableRandomPassword))
                    .googleSub(identity.sub())
                    .status("ACTIVE")
                    .roles(Set.of(customer))
                    .build();
            user = users.saveAndFlush(user);
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa");
        }
        return userService.createLoginResponse(user);
    }

    @Transactional(readOnly = true)
    public boolean isLinked(String currentEmail) {
        User user = findCurrentUser(currentEmail);
        return user.getGoogleSub() != null && !user.getGoogleSub().isBlank();
    }

    @Transactional
    public void link(String currentEmail, String idToken) {
        GoogleIdTokenService.GoogleIdentity identity = tokenService.verify(idToken);
        User user = findCurrentUser(currentEmail);
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa");
        }
        if (!user.getEmail().equalsIgnoreCase(identity.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email Google phải trùng với email tài khoản PPee đang đăng nhập");
        }
        User linked = users.findByGoogleSub(identity.sub()).orElse(null);
        if (linked != null && !linked.getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tài khoản Google này đã được liên kết với người dùng khác");
        }
        if (user.getGoogleSub() != null && !user.getGoogleSub().equals(identity.sub())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tài khoản PPee đã liên kết với Google khác");
        }
        user.setGoogleSub(identity.sub());
        users.saveAndFlush(user);
    }

    private User findCurrentUser(String currentEmail) {
        return users.findByEmailIgnoreCase(currentEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Phiên đăng nhập PPee không còn hợp lệ"));
    }
}
