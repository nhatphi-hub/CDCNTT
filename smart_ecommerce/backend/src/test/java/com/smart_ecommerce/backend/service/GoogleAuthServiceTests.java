package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.user.LoginResponse;
import com.smart_ecommerce.backend.entity.Role;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.RoleRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTests {
    @Mock GoogleIdTokenService tokenService;
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock PasswordEncoder encoder;
    @Mock UserService userService;
    @InjectMocks GoogleAuthService service;

    private final GoogleIdTokenService.GoogleIdentity identity =
            new GoogleIdTokenService.GoogleIdentity("google-123", "buyer@gmail.com", "Buyer", null);

    @Test
    void firstGoogleLoginCreatesOnlyCustomerWithUnusablePassword() {
        when(tokenService.verify("id-token")).thenReturn(identity);
        when(users.findByGoogleSub("google-123")).thenReturn(Optional.empty());
        when(users.existsByEmailIgnoreCase("buyer@gmail.com")).thenReturn(false);
        when(roles.findByNameIgnoreCase("CUSTOMER"))
                .thenReturn(Optional.of(Role.builder().name("CUSTOMER").build()));
        when(encoder.encode(anyString())).thenReturn("$2a$12$hash");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(userService.createLoginResponse(any(User.class)))
                .thenReturn(LoginResponse.builder().email("buyer@gmail.com").build());

        LoginResponse response = service.login("id-token");
        assertEquals("buyer@gmail.com", response.getEmail());
        verify(users).saveAndFlush(argThat(user ->
                "google-123".equals(user.getGoogleSub())
                        && "buyer@gmail.com".equals(user.getEmail())
                        && "$2a$12$hash".equals(user.getPassword())
                        && user.getRoles().size() == 1
                        && user.getRoles().iterator().next().getName().equals("CUSTOMER")));
    }

    @Test
    void sameEmailWithoutLinkRefusesAutomaticAccountTakeover() {
        when(tokenService.verify("id-token")).thenReturn(identity);
        when(users.findByGoogleSub("google-123")).thenReturn(Optional.empty());
        when(users.existsByEmailIgnoreCase("buyer@gmail.com")).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.login("id-token"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void linkedGoogleLoginKeepsExistingSellerRoles() {
        User user = User.builder().id(UUID.randomUUID()).email("buyer@gmail.com")
                .googleSub("google-123").status("ACTIVE")
                .roles(Set.of(Role.builder().name("CUSTOMER").build(),
                        Role.builder().name("SELLER").build())).build();
        when(tokenService.verify("id-token")).thenReturn(identity);
        when(users.findByGoogleSub("google-123")).thenReturn(Optional.of(user));
        when(userService.createLoginResponse(user)).thenReturn(LoginResponse.builder()
                .userId(user.getId()).roles(Set.of("CUSTOMER", "SELLER")).build());
        LoginResponse response = service.login("id-token");
        assertEquals(Set.of("CUSTOMER", "SELLER"), response.getRoles());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void blockedUserCannotSignInViaGoogle() {
        User user = User.builder().googleSub("google-123").status("BLOCKED").build();
        when(tokenService.verify("id-token")).thenReturn(identity);
        when(users.findByGoogleSub("google-123")).thenReturn(Optional.of(user));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.login("id-token"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(userService);
    }

    @Test
    void authenticatedUserCanLinkMatchingVerifiedEmail() {
        User current = User.builder().id(UUID.randomUUID())
                .email("buyer@gmail.com").status("ACTIVE").build();
        when(tokenService.verify("id-token")).thenReturn(identity);
        when(users.findByEmailIgnoreCase("buyer@gmail.com")).thenReturn(Optional.of(current));
        when(users.findByGoogleSub("google-123")).thenReturn(Optional.empty());
        when(users.saveAndFlush(current)).thenReturn(current);
        service.link("buyer@gmail.com", "id-token");
        assertEquals("google-123", current.getGoogleSub());
    }

    @Test
    void linkRequiresGoogleEmailToMatchSignedInPpeeAccount() {
        User current = User.builder().id(UUID.randomUUID())
                .email("different@gmail.com").status("ACTIVE").build();
        when(tokenService.verify("id-token")).thenReturn(identity);
        when(users.findByEmailIgnoreCase("different@gmail.com")).thenReturn(Optional.of(current));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.link("different@gmail.com", "id-token"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(users, never()).saveAndFlush(any());
    }
}
