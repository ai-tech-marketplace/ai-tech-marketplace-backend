package com.aitechmarketplace.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicCatalogEndpoints_shouldBeAccessibleWithoutAuth() throws Exception {
        // GET /api/v1/categories should not return 401 Unauthorized
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401);
                });

        // GET /api/v1/products should not return 401 Unauthorized
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401);
                });
    }

    @Test
    void adminEndpoints_withoutAuth_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void adminEndpoints_withCustomerRole_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/categories"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }
}
