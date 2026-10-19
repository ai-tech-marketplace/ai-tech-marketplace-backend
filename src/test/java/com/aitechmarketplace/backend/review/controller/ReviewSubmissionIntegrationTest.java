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
import com.aitechmarketplace.backend.review.repository.ReviewHelpfulVoteRepository;
import com.aitechmarketplace.backend.review.repository.ReviewImageRepository;
import com.aitechmarketplace.backend.review.repository.ReviewRepository;
import com.aitechmarketplace.backend.user.entity.User;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewSubmissionIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

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

    private User customer;
    private String customerToken;
    private Product product;
    private OrderItem orderItem;

    @BeforeEach
    void setUp() throws Exception {
        customer = createCustomerUser();
        customerToken = login(customer.getEmail());

        User seller = createCustomerUser();

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

        Product p = new Product();
        p.setSeller(seller);
        p.setCategory(category);
        p.setBrand(brand);
        p.setName("Gaming Monitor");
        p.setSlug("gaming-monitor-" + UUID.randomUUID());
        p.setSku("MON-" + UUID.randomUUID().toString().substring(0, 8));
        p.setPrice(new BigDecimal("500.00"));
        p.setStockQuantity(20);
        p.setStatus("ACTIVE");
        product = productRepository.save(p);
        createdProductIds.add(product.getId());

        Order order = new Order();
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8));
        order.setUser(customer);
        order.setStatus(OrderStatus.DELIVERED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setSubtotal(new BigDecimal("500.00"));
        order.setTotalAmount(new BigDecimal("500.00"));
        order.setShippingRecipientName("Recipient");
        order.setShippingPhone("0123456789");
        order.setShippingAddress("456 Avenue");
        Order savedOrder = orderRepository.save(order);
        createdOrderIds.add(savedOrder.getId());

        orderItem = new OrderItem(savedOrder, product, product.getName(), product.getSku(), 1, product.getPrice(), product.getPrice());
        orderItem = orderItemRepository.save(orderItem);
    }

    @AfterEach
    void tearDown() {
        helpfulVoteRepository.deleteAll();
        reviewImageRepository.deleteAll();
        reviewRepository.deleteAll();

        orderItemRepository.deleteAll();
        createdOrderIds.forEach(id -> {
            if (orderRepository.existsById(id)) orderRepository.deleteById(id);
        });
        createdOrderIds.clear();

        createdProductIds.forEach(id -> {
            if (productRepository.existsById(id)) productRepository.deleteById(id);
        });
        createdProductIds.clear();

        createdBrandIds.forEach(id -> {
            if (brandRepository.existsById(id)) brandRepository.deleteById(id);
        });
        createdBrandIds.clear();

        createdCategoryIds.forEach(id -> {
            if (categoryRepository.existsById(id)) categoryRepository.deleteById(id);
        });
        createdCategoryIds.clear();

        createdUserIds.forEach(id -> {
            if (userRepository.existsById(id)) userRepository.deleteById(id);
        });
        createdUserIds.clear();
    }

    @Test
    void submitAndEditReview_successFlow() throws Exception {
        String createPayload = """
                {
                    "orderItemId": %d,
                    "rating": 5,
                    "comment": "Tuyệt vời, màn hình 144Hz rất mượt",
                    "imageUrls": ["https://s3.example.com/img1.webp"]
                }
                """.formatted(orderItem.getId());

        String createRes = mockMvc.perform(
                post("/api/v1/products/" + product.getId() + "/reviews")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload)
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.rating", is(5)))
                .andExpect(jsonPath("$.data.comment", is("Tuyệt vời, màn hình 144Hz rất mượt")))
                .andExpect(jsonPath("$.data.isVerifiedPurchase", is(true)))
                .andExpect(jsonPath("$.data.isEdited", is(false)))
                .andExpect(jsonPath("$.data.images", hasSize(1)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(createRes);
        long reviewId = json.path("data").path("id").asLong();

        // Duplicate submission should fail with 409 Conflict
        mockMvc.perform(
                post("/api/v1/products/" + product.getId() + "/reviews")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload)
        )
                .andExpect(status().isConflict());

        // Update review
        String updatePayload = """
                {
                    "rating": 4,
                    "comment": "Dùng 1 tuần thấy tốt nhưng chân đế hơi to",
                    "imageUrls": []
                }
                """;

        mockMvc.perform(
                put("/api/v1/reviews/" + reviewId)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.rating", is(4)))
                .andExpect(jsonPath("$.data.comment", is("Dùng 1 tuần thấy tốt nhưng chân đế hơi to")))
                .andExpect(jsonPath("$.data.isEdited", is(true)));
    }

    private User createCustomerUser() {
        String email = "review-user-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);
        User user = userService.register(email, passwordHash, "Customer " + UUID.randomUUID(), null);
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
