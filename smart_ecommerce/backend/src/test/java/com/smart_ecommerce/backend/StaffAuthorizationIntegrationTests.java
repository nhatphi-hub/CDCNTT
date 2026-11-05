package com.smart_ecommerce.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StaffAuthorizationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCanReadManagementDataAndSubmitCatalogWritesButCannotUseAdminOnlyApis()
            throws Exception {
        mockMvc.perform(get("/api/orders/management"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/shipping"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/product-variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/orders/00000000-0000-0000-0000-000000000001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/payments/not-a-uuid"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotUseStaffOrderManagementOrShippingMutationApis() throws Exception {
        mockMvc.perform(get("/api/orders/management"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/shipping"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/shipping/orders/00000000-0000-0000-0000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
