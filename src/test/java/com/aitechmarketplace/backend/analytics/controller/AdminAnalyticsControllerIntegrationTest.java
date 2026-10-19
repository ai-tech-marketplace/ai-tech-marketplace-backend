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
import com.aitechmarketplace.backend.settlement.entity.SellerOrderSettlement;
import com.aitechmarketplace.backend.settlement.repository.SellerOrderSettlementRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAnalyticsControllerIntegrationTest {

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
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private SellerOrderSettlementRepository settlementRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void cleanup() {
        settlementRepository.deleteAll();
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
    void getOverview_unauthorized_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOverview_customerRole_shouldReturn403() throws Exception {
        User customer = createCustomerUser();
        String token = login(customer.getEmail());

        mockMvc.perform(get("/api/v1/admin/analytics/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAnalyticsEndpoints_withData_success() throws Exception {
        User admin = createAdminUser();
        String token = login(admin.getEmail());

        User seller = createCustomerUser();
        User buyer = createCustomerUser();

        Category category = new Category();
        category.setName("Gaming Laptops " + UUID.randomUUID());
        category.setSlug("gaming-laptops-" + UUID.randomUUID());
        category = categoryRepository.save(category);
        createdCategoryIds.add(category.getId());

        Product product = new Product();
        product.setName("RTX 5090 Laptop " + UUID.randomUUID());
        product.setSlug("rtx-5090-" + UUID.randomUUID());
        product.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        product.setPrice(new BigDecimal("50000000.00"));
        product.setStockQuantity(5);
        product.setSeller(seller);
        product.setCategory(category);
        product.setActive(true);
        product = productRepository.save(product);
        createdProductIds.add(product.getId());

        Order order = new Order();
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8));
        order.setUser(buyer);
        order.setStatus(OrderStatus.DELIVERED);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setSubtotal(new BigDecimal("50000000.00"));
        order.setTotalAmount(new BigDecimal("50000000.00"));
        order.setShippingRecipientName("Buyer");
        order.setShippingPhone("0123456789");
        order.setShippingAddress("123 Street");
        order = orderRepository.save(order);

        OrderItem item = new OrderItem(order, product, product.getName(), product.getSku(), 1, product.getPrice(), product.getPrice());
        item = orderItemRepository.save(item);

        SellerOrderSettlement settlement = new SellerOrderSettlement(
                order.getId(),
                item.getId(),
                seller.getId(),
                new BigDecimal("50000000.00"),
                new BigDecimal("0.0500"),
                new BigDecimal("2500000.00"),
                BigDecimal.ZERO,
                new BigDecimal("47500000.00")
        );
        settlementRepository.save(settlement);

        // 1. Overview
        mockMvc.perform(get("/api/v1/admin/analytics/overview")
                        .param("period", "30d")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.currency", is("VND")))
                .andExpect(jsonPath("$.data.totalOrders", is(1)))
                .andExpect(jsonPath("$.data.completedOrders", is(1)))
                .andExpect(jsonPath("$.data.cancelledOrders", is(0)))
                .andExpect(jsonPath("$.data.gmv", is(50000000.00)))
                .andExpect(jsonPath("$.data.netPlatformRevenue", is(2500000.00)));

        // 2. Top Sellers
        mockMvc.perform(get("/api/v1/admin/analytics/top-sellers")
                        .param("period", "30d")
                        .param("limit", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].sellerId", is(seller.getId().intValue())))
                .andExpect(jsonPath("$.data[0].grossSales", is(50000000.00)))
                .andExpect(jsonPath("$.data[0].commissionPaid", is(2500000.00)));

        // 3. Top Categories
        mockMvc.perform(get("/api/v1/admin/analytics/top-categories")
                        .param("period", "30d")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].categoryId", is(category.getId().intValue())))
                .andExpect(jsonPath("$.data[0].percentageShare", is(100.00)))
                .andExpect(jsonPath("$.data[0].platformCommissionEarned", is(2500000.00)));

        // 4. Revenue Chart
        mockMvc.perform(get("/api/v1/admin/analytics/revenue-chart")
                        .param("period", "7d")
                        .param("interval", "daily")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.interval", is("daily")))
                .andExpect(jsonPath("$.data.timeline", hasSize(1)))
                .andExpect(jsonPath("$.data.timeline[0].gmv", is(50000000.00)))
                .andExpect(jsonPath("$.data.timeline[0].commission", is(2500000.00)));
    }

    @Test
    void getOverview_invalidDateRange_shouldReturn400() throws Exception {
        User admin = createAdminUser();
        String token = login(admin.getEmail());

        mockMvc.perform(get("/api/v1/admin/analytics/overview")
                        .param("startDate", "2026-10-15")
                        .param("endDate", "2026-10-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_DATE_RANGE")));
    }

    private User createCustomerUser() {
        String email = "cust-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);
        User user = userService.register(email, passwordHash, "Customer " + UUID.randomUUID(), null);
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
