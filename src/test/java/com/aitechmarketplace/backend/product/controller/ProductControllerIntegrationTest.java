package com.aitechmarketplace.backend.product.controller;

import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import com.aitechmarketplace.backend.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        createdProductIds.forEach(id ->
            productRepository.findById(id)
                .ifPresent(productRepository::delete)
        );

        createdUserIds.forEach(id -> {
            if (userRepository.existsById(id)) {
                userRepository.deleteById(id);
            }
        });
    }

    @Test
    void productCrud_shouldWorkForOwner() throws Exception {
        User seller = createUser();

        String token = login(seller.getEmail());

        String createResponse = mockMvc.perform(
                post("/api/products")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "MacBook Pro",
                            "description": "M4 MacBook Pro",
                            "price": 35000000.00,
                            "stockQuantity": 5
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sellerId", is(seller.getId().intValue())))
            .andExpect(jsonPath("$.sellerEmail", is(seller.getEmail())))
            .andExpect(jsonPath("$.name", is("MacBook Pro")))
            .andExpect(jsonPath("$.description", is("M4 MacBook Pro")))
            .andExpect(jsonPath("$.price", is(35000000.00)))
            .andExpect(jsonPath("$.stockQuantity", is(5)))
            .andExpect(jsonPath("$.active", is(true)))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode createJson =
            objectMapper.readTree(createResponse);

        long productId =
            createJson.get("id").asLong();

        createdProductIds.add(productId);

        mockMvc.perform(
                get("/api/products/" + productId)
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is((int) productId)))
            .andExpect(jsonPath("$.name", is("MacBook Pro")));

        mockMvc.perform(
                get("/api/products/my")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id", is((int) productId)));

        mockMvc.perform(
                put("/api/products/" + productId)
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "MacBook Pro Updated",
                            "description": "Updated description",
                            "price": 36000000.00,
                            "stockQuantity": 10
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", is("MacBook Pro Updated")))
            .andExpect(jsonPath("$.description", is("Updated description")))
            .andExpect(jsonPath("$.price", is(36000000.00)))
            .andExpect(jsonPath("$.stockQuantity", is(10)));

        mockMvc.perform(
                delete("/api/products/" + productId)
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isNoContent());

        mockMvc.perform(
                get("/api/products/" + productId)
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active", is(false)));
    }

    @Test
    void createWithoutToken_shouldReturnUnauthorized()
        throws Exception {

        mockMvc.perform(
                post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "MacBook Pro",
                            "description": "M4 MacBook Pro",
                            "price": 35000000.00,
                            "stockQuantity": 5
                        }
                        """)
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createWithInvalidRequest_shouldReturnBadRequest()
        throws Exception {

        User seller = createUser();

        String token = login(seller.getEmail());

        mockMvc.perform(
                post("/api/products")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "",
                            "description": "Invalid product",
                            "price": -100,
                            "stockQuantity": -1
                        }
                        """)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateProductOwnedByAnotherUser_shouldReturnForbidden()
        throws Exception {

        User owner = createUser();
        User anotherUser = createUser();

        String ownerToken = login(owner.getEmail());
        String anotherUserToken = login(anotherUser.getEmail());

        String createResponse = mockMvc.perform(
                post("/api/products")
                    .header(
                        "Authorization",
                        "Bearer " + ownerToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Owner Product",
                            "description": "Owner product",
                            "price": 1000000.00,
                            "stockQuantity": 2
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long productId =
            objectMapper
                .readTree(createResponse)
                .get("id")
                .asLong();

        createdProductIds.add(productId);

        mockMvc.perform(
                put("/api/products/" + productId)
                    .header(
                        "Authorization",
                        "Bearer " + anotherUserToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Hacked Product",
                            "description": "Should not work",
                            "price": 9999999.00,
                            "stockQuantity": 99
                        }
                        """)
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void deleteProductOwnedByAnotherUser_shouldReturnForbidden()
        throws Exception {

        User owner = createUser();
        User anotherUser = createUser();

        String ownerToken = login(owner.getEmail());
        String anotherUserToken = login(anotherUser.getEmail());

        String createResponse = mockMvc.perform(
                post("/api/products")
                    .header(
                        "Authorization",
                        "Bearer " + ownerToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Owner Product",
                            "description": "Owner product",
                            "price": 1000000.00,
                            "stockQuantity": 2
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long productId =
            objectMapper
                .readTree(createResponse)
                .get("id")
                .asLong();

        createdProductIds.add(productId);

        mockMvc.perform(
                delete("/api/products/" + productId)
                    .header(
                        "Authorization",
                        "Bearer " + anotherUserToken
                    )
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void findById_whenProductDoesNotExist_shouldReturnNotFound()
        throws Exception {

        User seller = createUser();

        String token = login(seller.getEmail());

        mockMvc.perform(
                get("/api/products/999999999")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isNotFound());
    }

    private User createUser() {

        String email =
            "product-test-" +
            UUID.randomUUID() +
            "@example.com";

        String passwordHash =
            passwordEncoder.encode(PASSWORD);

        User user = userService.register(
            email,
            passwordHash,
            "Product",
            "Tester"
        );

        createdUserIds.add(user.getId());

        return user;
    }

    private String login(String email)
        throws Exception {

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
