package com.aitechmarketplace.backend.product.controller;

import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.service.CategoryService;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private RoleRepository roleRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();

    @AfterEach
    void cleanup() {

        createdProductIds.forEach(id ->
            productRepository.findById(id)
                .ifPresent(productRepository::delete)
        );

        createdCategoryIds.forEach(id -> {
            try {
                categoryService.deactivate(id);
            } catch (Exception ignored) {
                // Category may already have been removed.
            }
        });

        createdUserIds.forEach(id -> {
            if (userRepository.existsById(id)) {
                userRepository.deleteById(id);
            }
        });
    }

    @Test
    void productCrud_shouldWorkForOwner()
        throws Exception {

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
            .andExpect(
                jsonPath(
                    "$.sellerId",
                    is(seller.getId().intValue())
                )
            )
            .andExpect(
                jsonPath(
                    "$.sellerEmail",
                    is(seller.getEmail())
                )
            )
            .andExpect(
                jsonPath(
                    "$.name",
                    is("MacBook Pro")
                )
            )
            .andExpect(
                jsonPath(
                    "$.description",
                    is("M4 MacBook Pro")
                )
            )
            .andExpect(
                jsonPath(
                    "$.price",
                    is(35000000.00)
                )
            )
            .andExpect(
                jsonPath(
                    "$.stockQuantity",
                    is(5)
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
            .andExpect(
                jsonPath(
                    "$.id",
                    is((int) productId)
                )
            )
            .andExpect(
                jsonPath(
                    "$.name",
                    is("MacBook Pro")
                )
            );

        mockMvc.perform(
                get("/api/products/my")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(
                jsonPath(
                    "$[0].id",
                    is((int) productId)
                )
            );

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
            .andExpect(
                jsonPath(
                    "$.name",
                    is("MacBook Pro Updated")
                )
            )
            .andExpect(
                jsonPath(
                    "$.description",
                    is("Updated description")
                )
            )
            .andExpect(
                jsonPath(
                    "$.price",
                    is(36000000.00)
                )
            )
            .andExpect(
                jsonPath(
                    "$.stockQuantity",
                    is(10)
                )
            );

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
            .andExpect(
                jsonPath(
                    "$.active",
                    is(false)
                )
            );
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
    void adminCanUpdateProductOwnedByAnotherUser()
        throws Exception {

        User owner = createUser();
        User admin = createAdmin();

        String ownerToken = login(owner.getEmail());
        String adminToken = login(admin.getEmail());

        long productId = createProduct(
            ownerToken,
            "Owner Product",
            1000000.00,
            2
        );

        mockMvc.perform(
                put("/api/products/" + productId)
                    .header(
                        "Authorization",
                        "Bearer " + adminToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "Updated By Admin",
                            "description": "Admin updated product",
                            "price": 2000000.00,
                            "stockQuantity": 10
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.name",
                    is("Updated By Admin")
                )
            )
            .andExpect(
                jsonPath(
                    "$.price",
                    is(2000000.00)
                )
            )
            .andExpect(
                jsonPath(
                    "$.stockQuantity",
                    is(10)
                )
            );
    }

    @Test
    void updateStockOwnedByUser_shouldReturnOk()
        throws Exception {

        User seller = createUser();

        String token = login(seller.getEmail());

        long productId = createProduct(
            token,
            "Stock Product",
            1000.00,
            5
        );

        mockMvc.perform(
                patch("/api/products/{id}/stock", productId)
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "stockQuantity": 20
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.id",
                    is((int) productId)
                )
            )
            .andExpect(
                jsonPath(
                    "$.stockQuantity",
                    is(20)
                )
            );
    }

    @Test
    void updateStockOwnedByAnotherUser_shouldReturnForbidden()
        throws Exception {

        User owner = createUser();
        User anotherUser = createUser();

        String ownerToken = login(owner.getEmail());
        String anotherUserToken =
            login(anotherUser.getEmail());

        long productId = createProduct(
            ownerToken,
            "Other User Stock Product",
            1000.00,
            5
        );

        mockMvc.perform(
                patch("/api/products/{id}/stock", productId)
                    .header(
                        "Authorization",
                        "Bearer " + anotherUserToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "stockQuantity": 20
                        }
                        """)
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void adminUpdateStockOwnedByAnotherUser_shouldReturnOk()
        throws Exception {

        User owner = createUser();
        User admin = createAdmin();

        String ownerToken = login(owner.getEmail());
        String adminToken = login(admin.getEmail());

        long productId = createProduct(
            ownerToken,
            "Admin Stock Product",
            1000.00,
            5
        );

        mockMvc.perform(
                patch("/api/products/{id}/stock", productId)
                    .header(
                        "Authorization",
                        "Bearer " + adminToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "stockQuantity": 50
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.id",
                    is((int) productId)
                )
            )
            .andExpect(
                jsonPath(
                    "$.stockQuantity",
                    is(50)
                )
            );
    }

    @Test
    void updateStockWithNegativeQuantity_shouldReturnBadRequest()
        throws Exception {

        User seller = createUser();

        String token = login(seller.getEmail());

        long productId = createProduct(
            token,
            "Negative Stock Product",
            1000.00,
            5
        );

        mockMvc.perform(
                patch("/api/products/{id}/stock", productId)
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "stockQuantity": -1
                        }
                        """)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateStockForMissingProduct_shouldReturnNotFound()
        throws Exception {

        User seller = createUser();

        String token = login(seller.getEmail());

        mockMvc.perform(
                patch("/api/products/{id}/stock", 999999L)
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "stockQuantity": 20
                        }
                        """)
            )
            .andExpect(status().isNotFound());
    }

    @Test
    void adminCanDeactivateProductOwnedByAnotherUser()
        throws Exception {

        User owner = createUser();
        User admin = createAdmin();

        String ownerToken = login(owner.getEmail());
        String adminToken = login(admin.getEmail());

        long productId = createProduct(
            ownerToken,
            "Owner Product",
            1000000.00,
            2
        );

        mockMvc.perform(
                delete("/api/products/" + productId)
                    .header(
                        "Authorization",
                        "Bearer " + adminToken
                    )
            )
            .andExpect(status().isNoContent());

        mockMvc.perform(
                get("/api/products/" + productId)
                    .header(
                        "Authorization",
                        "Bearer " + adminToken
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

    @Test
    void search_shouldReturnPaginatedProducts()
        throws Exception {

        User seller = createUser();
        String token = login(seller.getEmail());

        createProduct(
            token,
            "iPhone 17",
            999.00,
            10
        );

        createProduct(
            token,
            "MacBook Pro",
            1999.00,
            5
        );

        createProduct(
            token,
            "AirPods Pro",
            299.00,
            20
        );

        mockMvc.perform(
                get("/api/products")
                    .param("page", "0")
                    .param("size", "2")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.content",
                    hasSize(2)
                )
            )
            .andExpect(
                jsonPath(
                    "$.page",
                    is(0)
                )
            )
            .andExpect(
                jsonPath(
                    "$.size",
                    is(2)
                )
            )
            .andExpect(
                jsonPath(
                    "$.totalElements",
                    is(3)
                )
            )
            .andExpect(
                jsonPath(
                    "$.totalPages",
                    is(2)
                )
            );
    }

    @Test
    void searchByKeyword_shouldReturnMatchingProducts()
        throws Exception {

        User seller = createUser();
        String token = login(seller.getEmail());

        createProduct(
            token,
            "iPhone 17 Pro",
            1299.00,
            10
        );

        createProduct(
            token,
            "MacBook Pro",
            1999.00,
            5
        );

        mockMvc.perform(
                get("/api/products")
                    .param("keyword", "iphone")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.content",
                    hasSize(1)
                )
            )
            .andExpect(
                jsonPath(
                    "$.content[0].name",
                    is("iPhone 17 Pro")
                )
            )
            .andExpect(
                jsonPath(
                    "$.totalElements",
                    is(1)
                )
            );
    }

    @Test
    void searchByCategory_shouldReturnMatchingProducts()
        throws Exception {

        User seller = createUser();
        String token = login(seller.getEmail());

        Category electronics =
            createCategory(
                "Electronics " + UUID.randomUUID()
            );

        Category fashion =
            createCategory(
                "Fashion " + UUID.randomUUID()
            );

        createProduct(
            token,
            "iPhone 17",
            1299.00,
            10,
            electronics.getId()
        );

        createProduct(
            token,
            "T-Shirt",
            29.00,
            20,
            fashion.getId()
        );

        mockMvc.perform(
                get("/api/products")
                    .param(
                        "categoryId",
                        electronics.getId().toString()
                    )
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.content",
                    hasSize(1)
                )
            )
            .andExpect(
                jsonPath(
                    "$.content[0].name",
                    is("iPhone 17")
                )
            )
            .andExpect(
                jsonPath(
                    "$.totalElements",
                    is(1)
                )
            );
    }

    @Test
    void searchByCategory_whenProductHasMultipleCategories_shouldNotReturnDuplicates()
        throws Exception {

        User seller = createUser();
        String token = login(seller.getEmail());

        Category electronics =
            createCategory(
                "Electronics " + UUID.randomUUID()
            );

        Category apple =
            createCategory(
                "Apple " + UUID.randomUUID()
            );

        Category fashion =
            createCategory(
                "Fashion " + UUID.randomUUID()
            );

        String productAResponse = mockMvc.perform(
                post("/api/products")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "iPhone 17 Pro",
                            "description": "Apple smartphone",
                            "price": 1299.00,
                            "stockQuantity": 10,
                            "categoryIds": [%d, %d]
                        }
                        """.formatted(
                            electronics.getId(),
                            apple.getId()
                        ))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long productAId =
            objectMapper
                .readTree(productAResponse)
                .get("id")
                .asLong();

        createdProductIds.add(productAId);

        createProduct(
            token,
            "MacBook Pro",
            1999.00,
            5,
            electronics.getId()
        );

        createProduct(
            token,
            "T-Shirt",
            29.00,
            20,
            fashion.getId()
        );

        mockMvc.perform(
                get("/api/products")
                    .param(
                        "categoryId",
                        electronics.getId().toString()
                    )
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.content",
                    hasSize(2)
                )
            )
            .andExpect(
                jsonPath(
                    "$.totalElements",
                    is(2)
                )
            )
            .andExpect(
                jsonPath(
                    "$.content[0].name",
                    is("MacBook Pro")
                )
            )
            .andExpect(
                jsonPath(
                    "$.content[1].name",
                    is("iPhone 17 Pro")
                )
            );
    }

    @Test
    void searchByPriceRange_shouldReturnMatchingProducts()
        throws Exception {

        User seller = createUser();
        String token = login(seller.getEmail());

        createProduct(
            token,
            "Cheap Product",
            50.00,
            10
        );

        createProduct(
            token,
            "Medium Product",
            500.00,
            10
        );

        createProduct(
            token,
            "Expensive Product",
            2000.00,
            10
        );

        mockMvc.perform(
                get("/api/products")
                    .param("minPrice", "100")
                    .param("maxPrice", "1000")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.content",
                    hasSize(1)
                )
            )
            .andExpect(
                jsonPath(
                    "$.content[0].name",
                    is("Medium Product")
                )
            )
            .andExpect(
                jsonPath(
                    "$.totalElements",
                    is(1)
                )
            );
    }

    @Test
    void searchWithMultipleFilters_shouldReturnMatchingProducts()
        throws Exception {

        User seller = createUser();
        String token = login(seller.getEmail());

        Category electronics =
            createCategory(
                "Electronics " + UUID.randomUUID()
            );

        createProduct(
            token,
            "iPhone 17 Pro",
            1299.00,
            10,
            electronics.getId()
        );

        createProduct(
            token,
            "iPhone 17 Case",
            29.00,
            50,
            electronics.getId()
        );

        createProduct(
            token,
            "MacBook Pro",
            1999.00,
            5
        );

        mockMvc.perform(
                get("/api/products")
                    .param("keyword", "iphone")
                    .param(
                        "categoryId",
                        electronics.getId().toString()
                    )
                    .param("minPrice", "1000")
                    .param("maxPrice", "1500")
                    .param("page", "0")
                    .param("size", "20")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.content",
                    hasSize(1)
                )
            )
            .andExpect(
                jsonPath(
                    "$.content[0].name",
                    is("iPhone 17 Pro")
                )
            )
            .andExpect(
                jsonPath(
                    "$.totalElements",
                    is(1)
                )
            );
    }

    @Test
    void search_shouldExcludeInactiveProducts()
        throws Exception {

        User seller = createUser();
        String token = login(seller.getEmail());

        long activeProductId =
            createProduct(
                token,
                "Active Product",
                100.00,
                10
            );

        long inactiveProductId =
            createProduct(
                token,
                "Inactive Product",
                200.00,
                10
            );

        mockMvc.perform(
                delete(
                    "/api/products/" +
                    inactiveProductId
                )
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isNoContent());

        mockMvc.perform(
                get("/api/products")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.content",
                    hasSize(1)
                )
            )
            .andExpect(
                jsonPath(
                    "$.content[0].id",
                    is((int) activeProductId)
                )
            )
            .andExpect(
                jsonPath(
                    "$.totalElements",
                    is(1)
                )
            );
    }

    private long createProduct(
        String token,
        String name,
        double price,
        int stockQuantity
    ) throws Exception {

        return createProduct(
            token,
            name,
            price,
            stockQuantity,
            null
        );
    }

    private long createProduct(
        String token,
        String name,
        double price,
        int stockQuantity,
        Long categoryId
    ) throws Exception {

        String categoryJson =
            categoryId == null
                ? ""
                : """
                    ,"categoryIds": [%d]
                    """.formatted(categoryId);

        String response = mockMvc.perform(
                post("/api/products")
                    .header(
                        "Authorization",
                        "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "name": "%s",
                            "description": "Test product",
                            "price": %s,
                            "stockQuantity": %d%s
                        }
                        """.formatted(
                            name,
                            price,
                            stockQuantity,
                            categoryJson
                        ))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long productId =
            objectMapper
                .readTree(response)
                .get("id")
                .asLong();

        createdProductIds.add(productId);

        return productId;
    }

    private Category createCategory(String name) {

        Category category =
            categoryService.create(
                name,
                "Test category"
            );

        createdCategoryIds.add(category.getId());

        return category;
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

    private User createAdmin() {

        String email =
            "product-admin-" +
            UUID.randomUUID() +
            "@example.com";

        String passwordHash =
            passwordEncoder.encode(PASSWORD);

        User admin = userService.register(
            email,
            passwordHash,
            "Product",
            "Admin"
        );

        Role adminRole =
            roleRepository
                .findByName(RoleName.ADMIN)
                .orElseThrow();

        admin.getRoles().add(adminRole);

        admin = userRepository.save(admin);

        createdUserIds.add(admin.getId());

        return admin;
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
