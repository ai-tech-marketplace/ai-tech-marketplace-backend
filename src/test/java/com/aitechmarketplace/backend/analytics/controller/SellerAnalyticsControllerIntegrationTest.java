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
class SellerAnalyticsControllerIntegrationTest {

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
        mockMvc.perform(get("/api/v1/seller/analytics/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOverview_customerRole_shouldReturn403() throws Exception {
        User customer = createCustomerUser();
        String token = login(customer.getEmail());

        mockMvc.perform(get("/api/v1/seller/analytics/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void sellerAnalyticsEndpoints_withData_success() throws Exception {
        User seller = createSellerUser();
        String sellerToken = login(seller.getEmail());

        User buyer = createCustomerUser();

        Category category = new Category();
        category.setName("Monitors " + UUID.randomUUID());
        category.setSlug("monitors-" + UUID.randomUUID());
        category = categoryRepository.save(category);
        createdCategoryIds.add(category.getId());

        Product product = new Product();
        product.setName("4K OLED Monitor " + UUID.randomUUID());
        product.setSlug("oled-4k-" + UUID.randomUUID());
        product.setSku("MON-" + UUID.randomUUID().toString().substring(0, 8));
        product.setPrice(new BigDecimal("20000000.00"));
        product.setStockQuantity(10);
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
        order.setSubtotal(new BigDecimal("20000000.00"));
        order.setTotalAmount(new BigDecimal("20000000.00"));
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
                new BigDecimal("20000000.00"),
                new BigDecimal("0.0500"),
                new BigDecimal("1000000.00"),
                BigDecimal.ZERO,
                new BigDecimal("19000000.00")
        );
        settlementRepository.save(settlement);

        // 1. Overview
        mockMvc.perform(get("/api/v1/seller/analytics/overview")
                        .param("period", "30d")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.grossSales", is(20000000.00)))
                .andExpect(jsonPath("$.data.commissionPaid", is(1000000.00)))
                .andExpect(jsonPath("$.data.netEarnings", is(19000000.00)))
                .andExpect(jsonPath("$.data.pendingSettlement", is(19000000.00)))
                .andExpect(jsonPath("$.data.totalOrders", is(1)))
                .andExpect(jsonPath("$.data.totalUnitsSold", is(1)));

        // 2. Top Products
        mockMvc.perform(get("/api/v1/seller/analytics/top-products")
                        .param("period", "30d")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].productId", is(product.getId().intValue())))
                .andExpect(jsonPath("$.data[0].unitsSold", is(1)))
                .andExpect(jsonPath("$.data[0].grossRevenue", is(20000000.00)));

        // 3. Order Settlements
        mockMvc.perform(get("/api/v1/seller/analytics/order-settlements")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].orderNumber", is(order.getOrderNumber())))
                .andExpect(jsonPath("$.data.content[0].sellerNetAmount", is(19000000.00)));
    }

    private User createCustomerUser() {
        String email = "cust-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);
        User user = userService.register(email, passwordHash, "Customer " + UUID.randomUUID(), null);
        createdUserIds.add(user.getId());
        return user;
    }

    private User createSellerUser() {
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);
        User user = userService.register(email, passwordHash, "Seller " + UUID.randomUUID(), null);
        Role sellerRole = roleRepository.findByName(RoleName.SELLER).orElseThrow();
        user.getRoles().add(sellerRole);
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
