package com.aitechmarketplace.backend.commission.controller;

import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.commission.entity.CommissionScope;
import com.aitechmarketplace.backend.commission.entity.CommissionSetting;
import com.aitechmarketplace.backend.commission.repository.CommissionSettingRepository;
import com.aitechmarketplace.backend.user.entity.Role;
import com.aitechmarketplace.backend.user.entity.RoleName;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.RoleRepository;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import com.aitechmarketplace.backend.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminCommissionControllerIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CommissionSettingRepository commissionSettingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void cleanup() {
        commissionSettingRepository.deleteAll();
        createdCategoryIds.forEach(id -> {
            if (categoryRepository.existsById(id)) {
                categoryRepository.deleteById(id);
            }
        });
        createdCategoryIds.clear();

        createdUserIds.forEach(id -> {
            if (userRepository.existsById(id)) {
                userRepository.deleteById(id);
            }
        });
        createdUserIds.clear();
    }

    @Test
    void getCommissionSettings_unauthorized_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/commission-settings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCommissionSettings_customerRole_shouldReturn403() throws Exception {
        User customer = createCustomerUser();
        String token = login(customer.getEmail());

        mockMvc.perform(get("/api/v1/admin/commission-settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullAdminCommissionFlow_createGetUpdateDelete_success() throws Exception {
        User admin = createAdminUser();
        String token = login(admin.getEmail());

        Category category = new Category();
        category.setName("Laptops " + UUID.randomUUID());
        category.setSlug("laptops-" + UUID.randomUUID());
        category = categoryRepository.save(category);
        createdCategoryIds.add(category.getId());

        // 1. Create CATEGORY commission setting
        String createJson = """
                {
                    "scope": "CATEGORY",
                    "targetId": %d,
                    "rate": 0.0250,
                    "description": "Laptops category rate (2.5%%)",
                    "isActive": true
                }
                """.formatted(category.getId());

        String createResponse = mockMvc.perform(post("/api/v1/admin/commission-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.scope", is("CATEGORY")))
                .andExpect(jsonPath("$.data.targetId", is(category.getId().intValue())))
                .andExpect(jsonPath("$.data.rate", is(0.0250)))
                .andReturn().getResponse().getContentAsString();

        JsonNode createdNode = objectMapper.readTree(createResponse);
        long settingId = createdNode.path("data").path("id").asLong();

        // 2. Get by ID
        mockMvc.perform(get("/api/v1/admin/commission-settings/" + settingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is((int) settingId)))
                .andExpect(jsonPath("$.data.targetName", is(category.getName())));

        // 3. List all with filter
        mockMvc.perform(get("/api/v1/admin/commission-settings")
                        .param("scope", "CATEGORY")
                        .param("isActive", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)));

        // 4. Update
        String updateJson = """
                {
                    "rate": 0.0300,
                    "description": "Updated laptops rate (3.0%)",
                    "isActive": true
                }
                """;

        mockMvc.perform(put("/api/v1/admin/commission-settings/" + settingId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.rate", is(0.0300)))
                .andExpect(jsonPath("$.data.description", is("Updated laptops rate (3.0%)")));

        // 5. Delete
        mockMvc.perform(delete("/api/v1/admin/commission-settings/" + settingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.message", is("Commission configuration successfully removed")));

        // 6. Verify deleted
        mockMvc.perform(get("/api/v1/admin/commission-settings/" + settingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_invalidRate_shouldReturn400() throws Exception {
        User admin = createAdminUser();
        String token = login(admin.getEmail());

        String invalidRateJson = """
                {
                    "scope": "GLOBAL",
                    "rate": 1.5000,
                    "description": "Exceeds 100%"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/commission-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRateJson))
                .andExpect(status().isBadRequest());
    }

    private User createCustomerUser() {
        String email = "customer-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);
        User user = userService.register(email, passwordHash, "Customer User", null);
        createdUserIds.add(user.getId());
        return user;
    }

    private User createAdminUser() {
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);
        User user = userService.register(email, passwordHash, "Admin User", null);
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        user.getRoles().add(adminRole);
        userRepository.save(user);
        createdUserIds.add(user.getId());
        return user;
    }

    private String login(String email) throws Exception {
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

        JsonNode json = objectMapper.readTree(response);
        return json.path("data").path("accessToken").asText();
    }
}
