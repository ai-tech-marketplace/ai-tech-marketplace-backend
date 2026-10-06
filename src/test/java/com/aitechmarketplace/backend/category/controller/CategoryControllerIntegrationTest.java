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

    private final ObjectMapper objectMapper =
        new ObjectMapper();

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

    private final List<Long> createdCategoryIds =
        new ArrayList<>();

    private final List<Long> createdUserIds =
        new ArrayList<>();

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

        String createResponse = mockMvc.perform(
                post("/api/categories")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Electronics",
                            "description": "Electronic products"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath(
                    "$.name",
                    is("Electronics")
                )
            )
            .andExpect(
                jsonPath(
                    "$.description",
                    is("Electronic products")
                )
            )
            .andExpect(
                jsonPath(
                    "$.active",
                    is(true)
                )
            )
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode createJson =
            objectMapper.readTree(createResponse);

        long categoryId =
            createJson.get("id").asLong();

        createdCategoryIds.add(categoryId);

        mockMvc.perform(
                get("/api/categories/" + categoryId)
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.id",
                    is((int) categoryId)
                )
            )
            .andExpect(
                jsonPath(
                    "$.name",
                    is("Electronics")
                )
            );

        mockMvc.perform(
                put("/api/categories/" + categoryId)
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Computers",
                            "description": "Computer products"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.name",
                    is("Computers")
                )
            )
            .andExpect(
                jsonPath(
                    "$.description",
                    is("Computer products")
                )
            );

        mockMvc.perform(
                delete("/api/categories/" + categoryId)
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isNoContent());

        mockMvc.perform(
                get("/api/categories/" + categoryId)
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.active",
                    is(false)
                )
            );
    }

    @Test
    void createDuplicateCategory_shouldReturnConflict()
        throws Exception {

        String token = createAndLoginAdmin();

        String categoryName =
            "Duplicate-" + System.nanoTime();

        String createResponse = mockMvc.perform(
                post("/api/categories")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "%s",
                            "description": "First category"
                        }
                        """.formatted(categoryName))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long categoryId =
            objectMapper
                .readTree(createResponse)
                .get("id")
                .asLong();

        createdCategoryIds.add(categoryId);

        mockMvc.perform(
                post("/api/categories")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "%s",
                            "description": "Duplicate category"
                        }
                        """.formatted(categoryName))
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath(
                    "$.error",
                    is("Category name already exists")
                )
            );
    }

    @Test
    void createInvalidCategory_shouldReturnBadRequest()
        throws Exception {

        String token = createAndLoginAdmin();

        mockMvc.perform(
                post("/api/categories")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "",
                            "description": "Invalid category"
                        }
                        """)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void findActiveCategories_shouldReturnOnlyActiveCategories()
        throws Exception {

        String token = createAndLoginAdmin();

        String activeName =
            "Active-" + System.nanoTime();

        String inactiveName =
            "Inactive-" + System.nanoTime();

        String activeResponse = mockMvc.perform(
                post("/api/categories")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "%s",
                            "description": "Active category"
                        }
                        """.formatted(activeName))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String inactiveResponse = mockMvc.perform(
                post("/api/categories")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "%s",
                            "description": "Inactive category"
                        }
                        """.formatted(inactiveName))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long activeId =
            objectMapper
                .readTree(activeResponse)
                .get("id")
                .asLong();

        long inactiveId =
            objectMapper
                .readTree(inactiveResponse)
                .get("id")
                .asLong();

        createdCategoryIds.add(activeId);
        createdCategoryIds.add(inactiveId);

        mockMvc.perform(
                delete("/api/categories/" + inactiveId)
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isNoContent());

        String listResponse = mockMvc.perform(
                get("/api/categories")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode categories =
            objectMapper.readTree(listResponse);

        boolean activeFound = false;
        boolean inactiveFound = false;

        for (JsonNode category : categories) {

            long id = category.get("id").asLong();

            if (id == activeId) {
                activeFound = true;
            }

            if (id == inactiveId) {
                inactiveFound = true;
            }
        }

        if (!activeFound) {
            throw new AssertionError(
                "Active category was not returned"
            );
        }

        if (inactiveFound) {
            throw new AssertionError(
                "Inactive category was returned"
            );
        }
    }

    @Test
    void findUnknownCategory_shouldReturnNotFound()
        throws Exception {

        String token = createAndLoginUser();

        mockMvc.perform(
                get("/api/categories/999999999")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isNotFound());
    }

    @Test
    void updateUnknownCategory_shouldReturnNotFound()
        throws Exception {

        String token = createAndLoginAdmin();

        mockMvc.perform(
                put("/api/categories/999999999")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Computers",
                            "description": "Computer products"
                        }
                        """)
            )
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteUnknownCategory_shouldReturnNotFound()
        throws Exception {

        String token = createAndLoginAdmin();

        mockMvc.perform(
                delete("/api/categories/999999999")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isNotFound());
    }

    @Test
    void userCreateCategory_shouldReturnForbidden()
        throws Exception {

        String token = createAndLoginUser();

        mockMvc.perform(
                post("/api/categories")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "User Category",
                            "description": "Should not be allowed"
                        }
                        """)
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void userUpdateCategory_shouldReturnForbidden()
        throws Exception {

        String adminToken = createAndLoginAdmin();

        String createResponse = mockMvc.perform(
                post("/api/categories")
                    .header(
                        "Authorization",
                        "Bearer " + adminToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Protected Category",
                            "description": "Protected"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long categoryId =
            objectMapper
                .readTree(createResponse)
                .get("id")
                .asLong();

        createdCategoryIds.add(categoryId);

        String userToken = createAndLoginUser();

        mockMvc.perform(
                put("/api/categories/" + categoryId)
                    .header(
                        "Authorization",
                        "Bearer " + userToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "User Updated",
                            "description": "Should not be allowed"
                        }
                        """)
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void userDeleteCategory_shouldReturnForbidden()
        throws Exception {

        String adminToken = createAndLoginAdmin();

        String createResponse = mockMvc.perform(
                post("/api/categories")
                    .header(
                        "Authorization",
                        "Bearer " + adminToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Protected Delete Category",
                            "description": "Protected"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long categoryId =
            objectMapper
                .readTree(createResponse)
                .get("id")
                .asLong();

        createdCategoryIds.add(categoryId);

        String userToken = createAndLoginUser();

        mockMvc.perform(
                delete("/api/categories/" + categoryId)
                    .header(
                        "Authorization",
                        "Bearer " + userToken
                    )
            )
            .andExpect(status().isForbidden());
    }

    private String createAndLoginUser()
        throws Exception {

        String email =
            "category-test-" +
            UUID.randomUUID() +
            "@example.com";

        String passwordHash =
            passwordEncoder.encode(PASSWORD);

        User user = userService.register(
            email,
            passwordHash,
            "Category",
            "Tester"
        );

        createdUserIds.add(user.getId());

        String response = mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "email": "%s",
                            "password": "%s"
                        }
                        """.formatted(
                            email,
                            PASSWORD
                        ))
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json =
            objectMapper.readTree(response);

        return json.get("token").asText();
    }

    private String createAndLoginAdmin()
        throws Exception {

        String email =
            "category-admin-test-" +
            UUID.randomUUID() +
            "@example.com";

        String passwordHash =
            passwordEncoder.encode(PASSWORD);

        User user = userService.register(
            email,
            passwordHash,
            "Category",
            "Admin"
        );

        Role adminRole =
            roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() ->
                    new IllegalStateException(
                        "ADMIN role not found"
                    )
                );

        user.getRoles().add(adminRole);
        userRepository.save(user);

        createdUserIds.add(user.getId());

        String response = mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "email": "%s",
                            "password": "%s"
                        }
                        """.formatted(
                            email,
                            PASSWORD
                        ))
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json =
            objectMapper.readTree(response);

        return json.get("token").asText();
    }
}