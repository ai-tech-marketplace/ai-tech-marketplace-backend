package com.aitechmarketplace.backend.category.controller;

import com.aitechmarketplace.backend.category.repository.CategoryRepository;
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
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CategoryControllerIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    private final List<Long> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        createdCategoryIds.forEach(id ->
            categoryRepository.findById(id)
                .ifPresent(categoryRepository::delete)
        );
        for (Long userId : createdUserIds) {
            if (userRepository.existsById(userId)) {
                userRepository.deleteById(userId);
            }
        }
        createdCategoryIds.clear();
        createdUserIds.clear();
    }

    @Test
    void categoryCrud_shouldWork() throws Exception {
        String token = createAndLoginAdmin();

        // Create
        String createResponse = mockMvc.perform(
                post("/api/v1/admin/categories")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Electronics",
                            "description": "Electronic products"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.name", is("Electronics")))
            .andExpect(jsonPath("$.data.slug", is("electronics")))
            .andExpect(jsonPath("$.data.description", is("Electronic products")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        long categoryId = createJson.get("data").get("id").asLong();
        createdCategoryIds.add(categoryId);

        // Read (public endpoint — no auth required)
        mockMvc.perform(get("/api/v1/categories/" + categoryId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.id", is((int) categoryId)))
            .andExpect(jsonPath("$.data.name", is("Electronics")));

        // Update
        mockMvc.perform(
                put("/api/v1/admin/categories/" + categoryId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Computers",
                            "description": "Computer products"
                        }
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.name", is("Computers")))
            .andExpect(jsonPath("$.data.slug", is("computers")))
            .andExpect(jsonPath("$.data.description", is("Computer products")));

        // Delete (deactivate)
        mockMvc.perform(
                delete("/api/v1/admin/categories/" + categoryId)
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        // Verify deactivated — GET still returns it but it won't appear in active list
        mockMvc.perform(get("/api/v1/categories/" + categoryId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id", is((int) categoryId)));
    }

    @Test
    void createDuplicateCategory_shouldReturnConflict() throws Exception {
        String token = createAndLoginAdmin();
        String categoryName = "Duplicate-" + System.nanoTime();

        String createResponse = mockMvc.perform(
                post("/api/v1/admin/categories")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "%s",
                            "description": "First category"
                        }
                        """.formatted(categoryName)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long categoryId = objectMapper.readTree(createResponse)
            .get("data").get("id").asLong();
        createdCategoryIds.add(categoryId);

        // Same name → same slug → conflict
        mockMvc.perform(
                post("/api/v1/admin/categories")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "%s",
                            "description": "Duplicate category"
                        }
                        """.formatted(categoryName)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.errorCode", is("SLUG_ALREADY_EXISTS")));
    }

    @Test
    void createInvalidCategory_shouldReturnBadRequest() throws Exception {
        String token = createAndLoginAdmin();

        mockMvc.perform(
                post("/api/v1/admin/categories")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "",
                            "description": "Invalid category"
                        }
                        """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void findActiveCategories_shouldReturnOnlyActiveCategories() throws Exception {
        String token = createAndLoginAdmin();

        String activeName = "Active-" + System.nanoTime();
        String inactiveName = "Inactive-" + System.nanoTime();

        String activeResponse = mockMvc.perform(
                post("/api/v1/admin/categories")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "%s",
                            "description": "Active category"
                        }
                        """.formatted(activeName)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String inactiveResponse = mockMvc.perform(
                post("/api/v1/admin/categories")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "%s",
                            "description": "Inactive category"
                        }
                        """.formatted(inactiveName)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long activeId = objectMapper.readTree(activeResponse)
            .get("data").get("id").asLong();
        long inactiveId = objectMapper.readTree(inactiveResponse)
            .get("data").get("id").asLong();

        createdCategoryIds.add(activeId);
        createdCategoryIds.add(inactiveId);

        // Deactivate one
        mockMvc.perform(
                delete("/api/v1/admin/categories/" + inactiveId)
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        // List active (public — no auth required)
        String listResponse = mockMvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode dataArray = objectMapper.readTree(listResponse).get("data");
        boolean activeFound = false;
        boolean inactiveFound = false;

        for (JsonNode category : dataArray) {
            long id = category.get("id").asLong();
            if (id == activeId) activeFound = true;
            if (id == inactiveId) inactiveFound = true;
        }

        if (!activeFound) {
            throw new AssertionError("Active category was not returned");
        }
        if (inactiveFound) {
            throw new AssertionError("Inactive category was returned");
        }
    }

    @Test
    void findUnknownCategory_shouldReturnNotFound() throws Exception {
        // Public endpoint — no auth required
        mockMvc.perform(get("/api/v1/categories/999999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.errorCode", is("CATEGORY_NOT_FOUND")));
    }

    @Test
    void updateUnknownCategory_shouldReturnNotFound() throws Exception {
        String token = createAndLoginAdmin();

        mockMvc.perform(
                put("/api/v1/admin/categories/999999999")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Computers",
                            "description": "Computer products"
                        }
                        """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.errorCode", is("CATEGORY_NOT_FOUND")));
    }

    @Test
    void deleteUnknownCategory_shouldReturnNotFound() throws Exception {
        String token = createAndLoginAdmin();

        mockMvc.perform(
                delete("/api/v1/admin/categories/999999999")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode", is("CATEGORY_NOT_FOUND")));
    }

    @Test
    void userCreateCategory_shouldReturnForbidden() throws Exception {
        String token = createAndLoginUser();

        mockMvc.perform(
                post("/api/v1/admin/categories")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "User Category",
                            "description": "Should not be allowed"
                        }
                        """))
            .andExpect(status().isForbidden());
    }

    @Test
    void userUpdateCategory_shouldReturnForbidden() throws Exception {
        String adminToken = createAndLoginAdmin();

        String createResponse = mockMvc.perform(
                post("/api/v1/admin/categories")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Protected Category",
                            "description": "Protected"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long categoryId = objectMapper.readTree(createResponse)
            .get("data").get("id").asLong();
        createdCategoryIds.add(categoryId);

        String userToken = createAndLoginUser();

        mockMvc.perform(
                put("/api/v1/admin/categories/" + categoryId)
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "User Updated",
                            "description": "Should not be allowed"
                        }
                        """))
            .andExpect(status().isForbidden());
    }

    @Test
    void userDeleteCategory_shouldReturnForbidden() throws Exception {
        String adminToken = createAndLoginAdmin();

        String createResponse = mockMvc.perform(
                post("/api/v1/admin/categories")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Protected Delete Category",
                            "description": "Protected"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long categoryId = objectMapper.readTree(createResponse)
            .get("data").get("id").asLong();
        createdCategoryIds.add(categoryId);

        String userToken = createAndLoginUser();

        mockMvc.perform(
                delete("/api/v1/admin/categories/" + categoryId)
                    .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void findActiveCategories_publicAccess_shouldWork() throws Exception {
        // Public catalog access — no auth header at all
        mockMvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)));
    }

    private String createAndLoginUser() throws Exception {
        String email = "category-test-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);

        User user = userService.register(email, passwordHash, "Category Tester", null);
        createdUserIds.add(user.getId());

        String response = mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "email": "%s",
                            "password": "%s"
                        }
                        """.formatted(email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.get("data").get("accessToken").asText();
    }

    private String createAndLoginAdmin() throws Exception {
        String email = "category-admin-test-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);

        User user = userService.register(email, passwordHash, "Category Admin", null);

        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
            .orElseThrow(() -> new IllegalStateException("ADMIN role not found"));

        user.getRoles().add(adminRole);
        userRepository.save(user);

        createdUserIds.add(user.getId());

        String response = mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "email": "%s",
                            "password": "%s"
                        }
                        """.formatted(email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.get("data").get("accessToken").asText();
    }
}
