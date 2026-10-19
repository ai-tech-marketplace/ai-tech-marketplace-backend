package com.aitechmarketplace.backend.analytics.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerAnalyticsControllerIntegrationTest {

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
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void cleanup() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        createdProductIds.forEach(id -> {
            if (productRepository.existsById(id)) productRepository.deleteById(id);
        });
        createdProductIds.clear();

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
    void getSpendingSummary_unauthorized_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/customer/analytics/spending-summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerAnalyticsEndpoints_withData_success() throws Exception {
        User customer = createCustomerUser();
        String token = login(customer.getEmail());

        User seller = createCustomerUser();

        Category category = new Category();
        category.setName("Mechanical Keyboards " + UUID.randomUUID());
        category.setSlug("mech-keyboards-" + UUID.randomUUID());
        category = categoryRepository.save(category);
        createdCategoryIds.add(category.getId());

        Product product = new Product();
        product.setName("Custom Keyboard " + UUID.randomUUID());
        product.setSlug("custom-kb-" + UUID.randomUUID());
        product.setSku("KB-" + UUID.randomUUID().toString().substring(0, 8));
        product.setPrice(new BigDecimal("3000000.00"));
        product.setStockQuantity(20);
        product.setSeller(seller);
        product.setCategory(category);
        product.setActive(true);
        product = productRepository.save(product);
        createdProductIds.add(product.getId());

        Order order = new Order();
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8));
        order.setUser(customer);
        order.setStatus(OrderStatus.DELIVERED);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setSubtotal(new BigDecimal("6000000.00"));
        order.setDiscountAmount(new BigDecimal("500000.00"));
        order.setTotalAmount(new BigDecimal("5500000.00"));
        order.setShippingRecipientName("Customer");
        order.setShippingPhone("0987654321");
        order.setShippingAddress("123 Street");
        order = orderRepository.save(order);

        OrderItem item = new OrderItem(order, product, product.getName(), product.getSku(), 2, product.getPrice(), new BigDecimal("6000000.00"));
        orderItemRepository.save(item);

        // 1. Spending Summary
        mockMvc.perform(get("/api/v1/customer/analytics/spending-summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.currency", is("VND")))
                .andExpect(jsonPath("$.data.totalSpent", is(5500000.00)))
                .andExpect(jsonPath("$.data.totalSaved", is(500000.00)))
                .andExpect(jsonPath("$.data.totalOrdersPlaced", is(1)))
                .andExpect(jsonPath("$.data.completedOrders", is(1)));

        // 2. Category Breakdown
        mockMvc.perform(get("/api/v1/customer/analytics/category-breakdown")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].categoryId", is(category.getId().intValue())))
                .andExpect(jsonPath("$.data[0].amountSpent", is(6000000.00)))
                .andExpect(jsonPath("$.data[0].percentageShare", is(100.00)))
                .andExpect(jsonPath("$.data[0].itemsPurchased", is(2)));

        // 3. Monthly Spending
        mockMvc.perform(get("/api/v1/customer/analytics/monthly-spending")
                        .param("months", "6")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.months", is(6)))
                .andExpect(jsonPath("$.data.timeline", hasSize(1)))
                .andExpect(jsonPath("$.data.timeline[0].amountSpent", is(5500000.00)))
                .andExpect(jsonPath("$.data.timeline[0].orderCount", is(1)));
    }

    private User createCustomerUser() {
        String email = "cust-" + UUID.randomUUID() + "@example.com";
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
