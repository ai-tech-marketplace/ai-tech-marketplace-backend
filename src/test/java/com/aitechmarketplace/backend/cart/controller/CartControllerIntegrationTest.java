package com.aitechmarketplace.backend.cart.controller;

import com.aitechmarketplace.backend.cart.entity.Cart;
import com.aitechmarketplace.backend.cart.repository.CartItemRepository;
import com.aitechmarketplace.backend.cart.repository.CartRepository;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.user.entity.Role;
import com.aitechmarketplace.backend.user.entity.RoleName;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.RoleRepository;
import com.aitechmarketplace.backend.user.repository.UserRepository;
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

import java.math.BigDecimal;
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
class CartControllerIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        createdUserIds.forEach(userId -> {
            try {
                cartRepository.findByUserId(userId).ifPresent(cartRepository::delete);
            } catch (Exception ignored) {
            }
        });

        createdProductIds.forEach(id -> {
            try {
                productRepository.findById(id).ifPresent(productRepository::delete);
            } catch (Exception ignored) {
            }
        });

        createdUserIds.forEach(id -> {
            try {
                if (userRepository.existsById(id)) {
                    userRepository.deleteById(id);
                }
            } catch (Exception ignored) {
            }
        });
    }

    @Test
    void getCart_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCart_shouldReturnEmptyCart_whenNewUser() throws Exception {
        User user = createUser();
        String token = login(user.getEmail());

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.totalItems", is(0)))
                .andExpect(jsonPath("$.data.totalAmount", is(0)));
    }

    @Test
    void addCartItem_shouldAddProductAndReturn200() throws Exception {
        User seller = createUser();
        Product product = createProduct(seller, 10, new BigDecimal("25000000.00"));

        User customer = createUser();
        String token = login(customer.getEmail());

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": %d,
                                    "quantity": 2
                                }
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Product added to cart")))
                .andExpect(jsonPath("$.data.productId", is(product.getId().intValue())))
                .andExpect(jsonPath("$.data.quantity", is(2)));

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].product.id", is(product.getId().intValue())))
                .andExpect(jsonPath("$.data.items[0].quantity", is(2)))
                .andExpect(jsonPath("$.data.items[0].subtotal", is(50000000.00)))
                .andExpect(jsonPath("$.data.totalItems", is(2)))
                .andExpect(jsonPath("$.data.totalAmount", is(50000000.00)));
    }

    @Test
    void addCartItem_shouldIncrementQuantity_whenProductAlreadyInCart() throws Exception {
        User seller = createUser();
        Product product = createProduct(seller, 10, new BigDecimal("1000000.00"));

        User customer = createUser();
        String token = login(customer.getEmail());

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": %d,
                                    "quantity": 2
                                }
                                """.formatted(product.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": %d,
                                    "quantity": 3
                                }
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity", is(5)));

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems", is(5)))
                .andExpect(jsonPath("$.data.totalAmount", is(5000000.00)));
    }

    @Test
    void addCartItem_shouldReturn400_whenQuantityZeroOrNegative() throws Exception {
        User customer = createUser();
        String token = login(customer.getEmail());

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": 1,
                                    "quantity": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addCartItem_shouldReturn400_whenStockExceeded() throws Exception {
        User seller = createUser();
        Product product = createProduct(seller, 2, new BigDecimal("1000000.00"));

        User customer = createUser();
        String token = login(customer.getEmail());

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": %d,
                                    "quantity": 5
                                }
                                """.formatted(product.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCartItem_shouldUpdateQuantityAndReturn200() throws Exception {
        User seller = createUser();
        Product product = createProduct(seller, 10, new BigDecimal("1000000.00"));

        User customer = createUser();
        String token = login(customer.getEmail());

        String addResult = mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": %d,
                                    "quantity": 2
                                }
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(addResult);
        long cartItemId = jsonNode.get("data").get("id").asLong();

        mockMvc.perform(put("/api/v1/cart/items/" + cartItemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "quantity": 4
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Cart item updated")))
                .andExpect(jsonPath("$.data.quantity", is(4)));

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems", is(4)))
                .andExpect(jsonPath("$.data.totalAmount", is(4000000.00)));
    }

    @Test
    void updateCartItem_shouldReturn404_whenItemNotOwned() throws Exception {
        User seller = createUser();
        Product product = createProduct(seller, 10, new BigDecimal("1000000.00"));

        User customerA = createUser();
        String tokenA = login(customerA.getEmail());

        String addResult = mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": %d,
                                    "quantity": 2
                                }
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long cartItemId = objectMapper.readTree(addResult).get("data").get("id").asLong();

        User customerB = createUser();
        String tokenB = login(customerB.getEmail());

        mockMvc.perform(put("/api/v1/cart/items/" + cartItemId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "quantity": 4
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCartItem_shouldReturn204() throws Exception {
        User seller = createUser();
        Product product = createProduct(seller, 10, new BigDecimal("1000000.00"));

        User customer = createUser();
        String token = login(customer.getEmail());

        String addResult = mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": %d,
                                    "quantity": 2
                                }
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long cartItemId = objectMapper.readTree(addResult).get("data").get("id").asLong();

        mockMvc.perform(delete("/api/v1/cart/items/" + cartItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void clearCart_shouldRemoveAllItemsAndReturn204() throws Exception {
        User seller = createUser();
        Product p1 = createProduct(seller, 10, new BigDecimal("1000000.00"));
        Product p2 = createProduct(seller, 10, new BigDecimal("2000000.00"));

        User customer = createUser();
        String token = login(customer.getEmail());

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": %d,
                                    "quantity": 1
                                }
                                """.formatted(p1.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": %d,
                                    "quantity": 2
                                }
                                """.formatted(p2.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.totalItems", is(0)));
    }

    @Test
    void compatibility_bothRoutePrefixesShouldWork() throws Exception {
        User user = createUser();
        String token = login(user.getEmail());

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Autowired
    private com.aitechmarketplace.backend.user.service.UserService userService;

    private User createUser() {
        String email = "cart-user-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);
        User user = userService.register(email, passwordHash, "Cart", "User");
        createdUserIds.add(user.getId());
        return user;
    }

    private Product createProduct(User seller, int stock, BigDecimal price) {
        Product product = new Product();
        product.setSeller(seller);
        product.setName("Product " + UUID.randomUUID());
        product.setDescription("Test Description");
        product.setPrice(price);
        product.setStockQuantity(stock);
        product.setActive(true);

        Product saved = productRepository.save(product);
        createdProductIds.add(saved.getId());
        return saved;
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.get("token").asText();
    }
}
