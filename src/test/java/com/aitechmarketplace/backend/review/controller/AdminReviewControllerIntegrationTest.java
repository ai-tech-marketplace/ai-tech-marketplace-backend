package com.aitechmarketplace.backend.review.controller;

import com.aitechmarketplace.backend.brand.entity.Brand;
import com.aitechmarketplace.backend.brand.repository.BrandRepository;
import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.OrderItem;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.order.entity.PaymentStatus;
import com.aitechmarketplace.backend.order.repository.OrderItemRepository;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.review.entity.Review;
import com.aitechmarketplace.backend.review.entity.ReviewStatus;
import com.aitechmarketplace.backend.review.repository.ReviewHelpfulVoteRepository;
import com.aitechmarketplace.backend.review.repository.ReviewImageRepository;
import com.aitechmarketplace.backend.review.repository.ReviewRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminReviewControllerIntegrationTest {

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
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewImageRepository reviewImageRepository;

    @Autowired
    private ReviewHelpfulVoteRepository helpfulVoteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdBrandIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();
    private final List<Long> createdOrderIds = new ArrayList<>();

    private String adminToken;
    private String customerToken;
    private Long testReviewId;
    private Long testProductId;

    @BeforeEach
    void setUp() throws Exception {
        // Create Admin user
        User admin = createUser("admin_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com", RoleName.ADMIN);
        adminToken = loginAndGetToken(admin.getEmail(), PASSWORD);

        // Create Seller user
        User seller = createUser("seller_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com", RoleName.SELLER);

        // Create Customer user
        User customer = createUser("customer_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com", RoleName.CUSTOMER);
        customerToken = loginAndGetToken(customer.getEmail(), PASSWORD);

        Category category = new Category();
        category.setName("Category " + UUID.randomUUID());
        category.setSlug("cat-" + UUID.randomUUID());
        category = categoryRepository.save(category);
        createdCategoryIds.add(category.getId());

        Brand brand = new Brand();
        brand.setName("Brand " + UUID.randomUUID());
        brand.setSlug("brand-" + UUID.randomUUID());
        brand = brandRepository.save(brand);
        createdBrandIds.add(brand.getId());

        Product product = new Product();
        product.setSeller(seller);
        product.setCategory(category);
        product.setBrand(brand);
        product.setName("Product " + UUID.randomUUID());
        product.setSlug("product-" + UUID.randomUUID());
        product.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        product.setDescription("Description");
        product.setPrice(BigDecimal.valueOf(100.00));
        product.setStockQuantity(10);
        product.setStatus("ACTIVE");
        product = productRepository.save(product);
        createdProductIds.add(product.getId());
        testProductId = product.getId();

        Order order = new Order();
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8));
        order.setUser(customer);
        order.setStatus(OrderStatus.DELIVERED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setSubtotal(product.getPrice());
        order.setTotalAmount(product.getPrice());
        order.setShippingRecipientName("Recipient");
        order.setShippingPhone("0123456789");
        order.setShippingAddress("Street");
        Order savedOrder = orderRepository.save(order);
        createdOrderIds.add(savedOrder.getId());

        OrderItem orderItem = new OrderItem(savedOrder, product, product.getName(), product.getSku(), 1, product.getPrice(), product.getPrice());
        orderItem = orderItemRepository.save(orderItem);

        Review review = new Review(customer, product, orderItem, 5, "Excellent product!");
        review = reviewRepository.save(review);
        testReviewId = review.getId();
    }

    @AfterEach
    void tearDown() {
        helpfulVoteRepository.deleteAll();
        reviewImageRepository.deleteAll();
        reviewRepository.deleteAll();
        orderItemRepository.deleteAll();
        for (Long orderId : createdOrderIds) {
            orderRepository.deleteById(orderId);
        }
        for (Long productId : createdProductIds) {
            productRepository.deleteById(productId);
        }
        for (Long brandId : createdBrandIds) {
            brandRepository.deleteById(brandId);
        }
        for (Long categoryId : createdCategoryIds) {
            categoryRepository.deleteById(categoryId);
        }
        for (Long userId : createdUserIds) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    void moderateReview_AdminHidesReview_Returns200AndHiddenStatus() throws Exception {
        String requestBody = """
                {
                    "status": "HIDDEN",
                    "hiddenReason": "Violates community guidelines: offensive content"
                }
                """;

        mockMvc.perform(patch("/api/v1/admin/reviews/{id}/status", testReviewId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.reviewId", is(testReviewId.intValue())))
                .andExpect(jsonPath("$.data.status", is("HIDDEN")))
                .andExpect(jsonPath("$.data.hiddenReason", is("Violates community guidelines: offensive content")));

        // Verify hidden review does not appear in public reviews listing
        mockMvc.perform(get("/api/v1/products/{id}/reviews", testProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements", is(0)));
    }

    @Test
    void moderateReview_AdminRestoresReviewToVisible_Returns200() throws Exception {
        // First hide it
        String hideBody = """
                {
                    "status": "HIDDEN",
                    "hiddenReason": "Temporary review hold"
                }
                """;

        mockMvc.perform(patch("/api/v1/admin/reviews/{id}/status", testReviewId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hideBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("HIDDEN")));

        // Restore to VISIBLE
        String restoreBody = """
                {
                    "status": "VISIBLE"
                }
                """;

        mockMvc.perform(patch("/api/v1/admin/reviews/{id}/status", testReviewId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(restoreBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VISIBLE")))
                .andExpect(jsonPath("$.data.hiddenReason").doesNotExist());

        // Verify it is visible again publicly
        mockMvc.perform(get("/api/v1/products/{id}/reviews", testProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements", is(1)));
    }

    @Test
    void moderateReview_HideWithoutReason_Returns400BadRequest() throws Exception {
        String requestBody = """
                {
                    "status": "HIDDEN"
                }
                """;

        mockMvc.perform(patch("/api/v1/admin/reviews/{id}/status", testReviewId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moderateReview_CustomerCallingAdminEndpoint_Returns403Forbidden() throws Exception {
        String requestBody = """
                {
                    "status": "HIDDEN",
                    "hiddenReason": "Not allowed"
                }
                """;

        mockMvc.perform(patch("/api/v1/admin/reviews/{id}/status", testReviewId)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderateReview_Unauthenticated_Returns401Unauthorized() throws Exception {
        String requestBody = """
                {
                    "status": "HIDDEN",
                    "hiddenReason": "Not allowed"
                }
                """;

        mockMvc.perform(patch("/api/v1/admin/reviews/{id}/status", testReviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    private User createUser(String email, RoleName roleName) {
        User user = userService.register(email, passwordEncoder.encode(PASSWORD), "User " + email, "0123456789");
        if (roleName != RoleName.CUSTOMER) {
            Role role = roleRepository.findByName(roleName).orElseThrow();
            user.getRoles().add(role);
            user = userRepository.save(user);
        }
        createdUserIds.add(user.getId());
        return user;
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String loginPayload = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        return jsonNode.get("data").get("accessToken").asText();
    }
}
