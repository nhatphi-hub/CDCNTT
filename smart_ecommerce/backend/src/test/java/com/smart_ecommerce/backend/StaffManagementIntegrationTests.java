package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.entity.Role;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.RoleRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class StaffManagementIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListAndViewStaffWithoutReceivingPassword() throws Exception {
        User staff = createUser("STAFF");

        mockMvc.perform(get("/api/staff"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(staff.getEmail())))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("password"))));
        mockMvc.perform(get("/api/staff/{userId}", staff.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("STAFF")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanGrantStaffWithoutRemovingCustomer() throws Exception {
        User customer = createUser("CUSTOMER");

        mockMvc.perform(post("/api/staff/{userId}", customer.getId()))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("STAFF")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CUSTOMER")));

        User updated = userRepository.findById(customer.getId()).orElseThrow();
        assertTrue(hasRole(updated, "CUSTOMER"));
        assertTrue(hasRole(updated, "STAFF"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateStaffGrantReturnsConflictAndDoesNotDuplicateTheRole() throws Exception {
        User staff = createUser("CUSTOMER", "STAFF");

        mockMvc.perform(post("/api/staff/{userId}", staff.getId()))
                .andExpect(status().isConflict());

        User unchanged = userRepository.findById(staff.getId()).orElseThrow();
        assertEquals(2, unchanged.getRoles().size());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void revokeRemovesOnlyStaffAndKeepsTheUserAndCustomerRole() throws Exception {
        User staff = createUser("CUSTOMER", "STAFF");

        mockMvc.perform(delete("/api/staff/{userId}", staff.getId()))
                .andExpect(status().isNoContent());

        User updated = userRepository.findById(staff.getId()).orElseThrow();
        assertTrue(hasRole(updated, "CUSTOMER"));
        assertFalse(hasRole(updated, "STAFF"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotListOrGrantStaffEvenForTheirOwnAccount() throws Exception {
        User customer = createUser("CUSTOMER");

        mockMvc.perform(get("/api/staff")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/staff/{userId}", customer.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCannotListOrGrantStaff() throws Exception {
        User staff = createUser("CUSTOMER", "STAFF");

        mockMvc.perform(get("/api/staff")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/staff/{userId}", staff.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/staff")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/staff/{userId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicRegistrationContinuesToAssignOnlyCustomerRole() throws Exception {
        String email = "registered-" + UUID.randomUUID() + "@example.test";

        mockMvc.perform(post("/api/users/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Customer\",\"email\":\""
                                + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("password"))));

        User registered = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertTrue(hasRole(registered, "CUSTOMER"));
        assertFalse(hasRole(registered, "STAFF"));
        assertFalse(hasRole(registered, "ADMIN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void missingUserReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/staff/{userId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/staff/{userId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void staffManagementNeverRemovesAdminAndWillNotGrantStaffToAdminAccount() throws Exception {
        User admin = createUser("ADMIN");

        mockMvc.perform(post("/api/staff/{userId}", admin.getId()))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/staff/{userId}", admin.getId()))
                .andExpect(status().isNoContent());

        User unchanged = userRepository.findById(admin.getId()).orElseThrow();
        assertTrue(hasRole(unchanged, "ADMIN"));
        assertFalse(hasRole(unchanged, "STAFF"));

        User staffAdmin = createUser("ADMIN", "STAFF");
        mockMvc.perform(delete("/api/staff/{userId}", staffAdmin.getId()))
                .andExpect(status().isNoContent());
        User revoked = userRepository.findById(staffAdmin.getId()).orElseThrow();
        assertTrue(hasRole(revoked, "ADMIN"));
        assertFalse(hasRole(revoked, "STAFF"));
    }

    private User createUser(String... roleNames) {
        Set<Role> roles = new HashSet<>();
        for (String roleName : roleNames) {
            roles.add(roleRepository.findByNameIgnoreCase(roleName).orElseThrow());
        }
        String suffix = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(User.builder()
                .fullName("Staff test user")
                .email("staff-test-" + suffix + "@example.test")
                .password("not-a-real-password-hash")
                .status("ACTIVE")
                .roles(roles)
                .build());
    }

    private boolean hasRole(User user, String expectedRole) {
        return user.getRoles().stream()
                .anyMatch(role -> expectedRole.equalsIgnoreCase(role.getName()));
    }
}
