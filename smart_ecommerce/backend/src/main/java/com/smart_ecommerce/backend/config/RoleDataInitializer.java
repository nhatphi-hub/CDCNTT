package com.smart_ecommerce.backend.config;

import com.smart_ecommerce.backend.entity.Role;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.RoleRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoleDataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap-admin.email:}")
    private String bootstrapAdminEmail;

    @Value("${app.bootstrap-admin.password:}")
    private String bootstrapAdminPassword;

    @Value("${app.bootstrap-admin.full-name:Administrator}")
    private String bootstrapAdminFullName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureRole("CUSTOMER");
        ensureRole("STAFF");
        ensureRole("SELLER");
        Role adminRole = ensureRole("ADMIN");

        boolean hasEmail = StringUtils.hasText(bootstrapAdminEmail);
        boolean hasPassword = StringUtils.hasText(bootstrapAdminPassword);
        if (!hasEmail && !hasPassword) {
            return;
        }
        if (!hasEmail || !hasPassword) {
            throw new IllegalStateException(
                    "Set both BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD, or leave both unset");
        }

        String email = normalizeEmail(bootstrapAdminEmail);
        if (email.length() > 150
                || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_EMAIL must be a valid email address of at most 150 characters");
        }
        if (bootstrapAdminPassword.length() < 12
                || bootstrapAdminPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD must be 12-72 UTF-8 bytes for BCrypt");
        }
        if (!StringUtils.hasText(bootstrapAdminFullName)
                || bootstrapAdminFullName.trim().length() > 150) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_FULL_NAME must contain 1-150 characters");
        }

        var existingUser = userRepository.findByEmailIgnoreCase(email);
        if (existingUser.isPresent()) {
            boolean alreadyAdmin = existingUser.get().getRoles().stream()
                    .map(Role::getName)
                    .anyMatch(name -> "ADMIN".equals(normalizeRoleName(name)));
            if (!alreadyAdmin) {
                log.warn("Bootstrap admin email already belongs to a non-admin account; no role was changed");
            }
            return;
        }

        User admin = User.builder()
                .fullName(bootstrapAdminFullName.trim())
                .email(email)
                .password(passwordEncoder.encode(bootstrapAdminPassword))
                .status("ACTIVE")
                .roles(Set.of(adminRole))
                .build();
        userRepository.save(admin);
        log.info("Created the one-time bootstrap ADMIN account from environment configuration");
    }

    private Role ensureRole(String name) {
        return roleRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name(name).build()));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRoleName(String roleName) {
        String normalized = roleName == null
                ? ""
                : roleName.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_")
                ? normalized.substring("ROLE_".length())
                : normalized;
    }
}
