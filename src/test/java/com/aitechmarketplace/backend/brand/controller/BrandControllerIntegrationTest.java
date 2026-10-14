package com.aitechmarketplace.backend.brand.controller;

import com.aitechmarketplace.backend.brand.entity.Brand;
import com.aitechmarketplace.backend.brand.repository.BrandRepository;
import com.aitechmarketplace.backend.user.entity.Role;
import com.aitechmarketplace.backend.user.entity.RoleName;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.RoleRepository;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import com.aitechmarketplace.backend.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BrandControllerIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    private final List<Long> createdBrandIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        createdBrandIds.forEach(id ->
            brandRepository.findById(id)
                .ifPresent(brandRepository::delete)
        );
        for (Long userId : createdUserIds) {
            if (userRepository.existsById(userId)) {
                userRepository.deleteById(userId);
            }
        }
        createdBrandIds.clear();
        createdUserIds.clear();
    }

    @Test
    void brandCrud_shouldWork() throws Exception {
        String token = createAndLoginAdmin();

        // Create
        String createResponse = mockMvc.perform(
                post("/api/v1/admin/brands")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Razer-%s",
                            "slug": "razer-%s"
                        }
                    """.formatted(System.nanoTime(), System.nanoTime()))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Brand created successfully"))
            .andExpect(jsonPath("$.data.id").isNumber())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        Long brandId = objectMapper.readTree(createResponse).path("data").path("id").asLong();
        createdBrandIds.add(brandId);

        // Get by ID (public access, no auth header)
        mockMvc.perform(get("/api/v1/brands/" + brandId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(brandId))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // Update
        mockMvc.perform(
                put("/api/v1/admin/brands/" + brandId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Razer Gaming-%s",
                            "slug": "razer-gaming-%s",
                            "status": "ACTIVE"
                        }
                    """.formatted(System.nanoTime(), System.nanoTime()))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Brand updated successfully"))
            .andExpect(jsonPath("$.data.name").value(org.hamcrest.Matchers.startsWith("Razer Gaming-")));

        // Deactivate (soft delete)
        mockMvc.perform(
                delete("/api/v1/admin/brands/" + brandId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isNoContent());

        // Verify deactivated status
        mockMvc.perform(get("/api/v1/brands/" + brandId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void publicEndpoints_shouldNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/brands"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void adminEndpoints_withoutToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(
                post("/api/v1/admin/brands")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Sony",
                            "slug": "sony"
                        }
                    """)
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoints_withCustomerToken_shouldReturnForbidden() throws Exception {
        String customerToken = createAndLoginCustomer();

        mockMvc.perform(
                post("/api/v1/admin/brands")
                    .header("Authorization", "Bearer " + customerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Sony",
                            "slug": "sony"
                        }
                    """)
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void create_withDuplicateName_shouldReturn409() throws Exception {
        String token = createAndLoginAdmin();
        String name = "DupName-" + System.nanoTime();

        String createResponse = mockMvc.perform(
                post("/api/v1/admin/brands")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "%s",
                            "slug": "slug-1-%s"
                        }
                    """.formatted(name, System.nanoTime()))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Long brandId = objectMapper.readTree(createResponse).path("data").path("id").asLong();
        createdBrandIds.add(brandId);

        mockMvc.perform(
                post("/api/v1/admin/brands")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "%s",
                            "slug": "slug-2-%s"
                        }
                    """.formatted(name, System.nanoTime()))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("BRAND_ALREADY_EXISTS"));
    }

    @Test
    void create_withDuplicateSlug_shouldReturn409() throws Exception {
        String token = createAndLoginAdmin();
        String slug = "dup-slug-" + System.nanoTime();

        String createResponse = mockMvc.perform(
                post("/api/v1/admin/brands")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Brand-1-%s",
                            "slug": "%s"
                        }
                    """.formatted(System.nanoTime(), slug))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Long brandId = objectMapper.readTree(createResponse).path("data").path("id").asLong();
        createdBrandIds.add(brandId);

        mockMvc.perform(
                post("/api/v1/admin/brands")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Brand-2-%s",
                            "slug": "%s"
                        }
                    """.formatted(System.nanoTime(), slug))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("SLUG_ALREADY_EXISTS"));
    }

    @Test
    void create_withInvalidPayload_shouldReturn400() throws Exception {
        String token = createAndLoginAdmin();

        mockMvc.perform(
                post("/api/v1/admin/brands")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": ""
                        }
                    """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void findById_nonExistent_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/brands/999999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("BRAND_NOT_FOUND"));
    }

    @Test
    void findActiveBrands_shouldReturnOnlyActiveBrands() throws Exception {
        String token = createAndLoginAdmin();

        String resp1 = mockMvc.perform(
                post("/api/v1/admin/brands")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Active-%s"
                        }
                    """.formatted(System.nanoTime()))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        Long activeId = objectMapper.readTree(resp1).path("data").path("id").asLong();
        createdBrandIds.add(activeId);

        String resp2 = mockMvc.perform(
                post("/api/v1/admin/brands")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Inactive-%s"
                        }
                    """.formatted(System.nanoTime()))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        Long inactiveId = objectMapper.readTree(resp2).path("data").path("id").asLong();
        createdBrandIds.add(inactiveId);

        mockMvc.perform(
                delete("/api/v1/admin/brands/" + inactiveId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isNoContent());

        String listResponse = mockMvc.perform(get("/api/v1/brands"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode data = objectMapper.readTree(listResponse).path("data");
        List<Long> ids = new ArrayList<>();
        data.forEach(node -> ids.add(node.path("id").asLong()));

        assertThat(ids).contains(activeId);
        assertThat(ids).doesNotContain(inactiveId);
    }

    private String createAndLoginAdmin() throws Exception {
        String email = "brand-admin-test-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);

        User user = userService.register(email, passwordHash, "Brand Admin", null);

        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
            .orElseThrow(() -> new IllegalStateException("ADMIN role not found"));

        user.getRoles().add(adminRole);
        userRepository.save(user);

        createdUserIds.add(user.getId());

        return loginAndGetToken(email);
    }

    private String createAndLoginCustomer() throws Exception {
        String email = "brand-cust-test-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);

        User user = userService.register(email, passwordHash, "Brand Customer", null);
        createdUserIds.add(user.getId());

        return loginAndGetToken(email);
    }

    private String loginAndGetToken(String email) throws Exception {
        String response = mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "email": "%s",
                            "password": "%s"
                        }
                    """.formatted(email, PASSWORD))
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode rootNode = objectMapper.readTree(response);
        return rootNode.path("data").path("accessToken").asText();
    }
}
