package com.aitechmarketplace.backend.coupon.controller;

import com.aitechmarketplace.backend.brand.entity.Brand;
import com.aitechmarketplace.backend.brand.repository.BrandRepository;
import com.aitechmarketplace.backend.cart.entity.Cart;
import com.aitechmarketplace.backend.cart.entity.CartItem;
import com.aitechmarketplace.backend.cart.repository.CartItemRepository;
import com.aitechmarketplace.backend.cart.repository.CartRepository;
import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.coupon.entity.Coupon;
import com.aitechmarketplace.backend.coupon.entity.DiscountType;
import com.aitechmarketplace.backend.coupon.repository.CouponRepository;
import com.aitechmarketplace.backend.coupon.repository.CouponUsageRepository;
import com.aitechmarketplace.backend.product.entity.Product;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CouponControllerIntegrationTest {

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
    private CouponRepository couponRepository;

    @Autowired
    private CouponUsageRepository couponUsageRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdBrandIds = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void cleanup() {
        couponUsageRepository.deleteAll();
        couponRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();

        createdProductIds.forEach(id -> {
            if (productRepository.existsById(id)) {
                productRepository.deleteById(id);
            }
        });
        createdProductIds.clear();

        createdCategoryIds.forEach(id -> {
            if (categoryRepository.existsById(id)) {
                categoryRepository.deleteById(id);
            }
        });
        createdCategoryIds.clear();

        createdBrandIds.forEach(id -> {
            if (brandRepository.existsById(id)) {
                brandRepository.deleteById(id);
            }
        });
        createdBrandIds.clear();

        createdUserIds.forEach(id -> {
            if (userRepository.existsById(id)) {
                userRepository.deleteById(id);
            }
        });
        createdUserIds.clear();
    }

    @Test
    void getPublicCoupons_shouldReturnActivePublicCouponsWithoutAuth() throws Exception {
        Coupon c1 = createCoupon("WELCOME10", "Welcome 10%", DiscountType.PERCENTAGE, new BigDecimal("10.00"), true, "ACTIVE");
        Coupon c2 = createCoupon("PRIVATE50", "Secret Voucher", DiscountType.FIXED_AMOUNT, new BigDecimal("50000.00"), false, "ACTIVE");

        mockMvc.perform(get("/api/v1/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].code", is("WELCOME10")));
    }

    @Test
    void validateCoupon_withValidCart_shouldReturnDiscount() throws Exception {
        User user = createUser();
        String token = login(user.getEmail());

        Product product = createProduct(user, new BigDecimal("1000000.00"));
        Cart cart = cartRepository.findByUserId(user.getId()).orElseGet(() -> cartRepository.save(new Cart(user)));
        cartItemRepository.save(new CartItem(cart, product, 2)); // Subtotal: 2,000,000

        createCoupon("SAVE10", "10% off", DiscountType.PERCENTAGE, new BigDecimal("10.00"), true, "ACTIVE");

        mockMvc.perform(post("/api/v1/coupons/validate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "couponCode": "SAVE10"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.valid", is(true)))
                .andExpect(jsonPath("$.data.code", is("SAVE10")))
                .andExpect(jsonPath("$.data.discountAmount", is(200000.0)))
                .andExpect(jsonPath("$.data.subtotal", is(2000000.0)))
                .andExpect(jsonPath("$.data.estimatedTotal", is(1830000.0))); // 2M + 30k - 200k
    }

    @Test
    void validateCoupon_belowMinOrder_shouldReturn400() throws Exception {
        User user = createUser();
        String token = login(user.getEmail());

        Product product = createProduct(user, new BigDecimal("500000.00"));
        Cart cart = cartRepository.findByUserId(user.getId()).orElseGet(() -> cartRepository.save(new Cart(user)));
        cartItemRepository.save(new CartItem(cart, product, 1)); // Subtotal: 500,000

        Coupon coupon = createCoupon("MIN1M", "10% off min 1M", DiscountType.PERCENTAGE, new BigDecimal("10.00"), true, "ACTIVE");
        coupon.setMinOrderValue(new BigDecimal("1000000.00"));
        couponRepository.save(coupon);

        mockMvc.perform(post("/api/v1/coupons/validate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "couponCode": "MIN1M"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("COUPON_MIN_ORDER_NOT_MET")));
    }

    @Test
    void adminEndpoints_asAdmin_shouldCreateAndListCoupons() throws Exception {
        User admin = createAdminUser();
        String token = login(admin.getEmail());

        mockMvc.perform(post("/api/v1/admin/coupons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "ADMIN2026",
                                    "name": "Admin Campaign",
                                    "description": "Admin campaign discount",
                                    "discountType": "FIXED_AMOUNT",
                                    "discountValue": 100000.00,
                                    "minOrderValue": 500000.00,
                                    "usageLimit": 50,
                                    "usageLimitPerUser": 1,
                                    "isPublic": true,
                                    "startDate": "2026-01-01T00:00:00Z",
                                    "endDate": "2026-12-31T23:59:59Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.code", is("ADMIN2026")));

        mockMvc.perform(get("/api/v1/admin/coupons")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].code", is("ADMIN2026")));
    }

    @Test
    void adminEndpoints_asCustomer_shouldReturnForbidden() throws Exception {
        User customer = createUser();
        String token = login(customer.getEmail());

        mockMvc.perform(post("/api/v1/admin/coupons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "HACKER",
                                    "name": "Unauthorized Campaign",
                                    "discountType": "FIXED_AMOUNT",
                                    "discountValue": 100000.00,
                                    "usageLimit": 10,
                                    "startDate": "2026-01-01T00:00:00Z",
                                    "endDate": "2026-12-31T23:59:59Z"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private User createUser() {
        String email = "coupon-user-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);
        User user = userService.register(email, passwordHash, "Coupon User", null);
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

    private Coupon createCoupon(String code, String name, DiscountType type, BigDecimal value, boolean isPublic, String status) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setName(name);
        coupon.setDiscountType(type);
        coupon.setDiscountValue(value);
        coupon.setMaxDiscountAmount(new BigDecimal("500000.00"));
        coupon.setMinOrderValue(BigDecimal.ZERO);
        coupon.setUsageLimit(100);
        coupon.setUsedCount(0);
        coupon.setUsageLimitPerUser(1);
        coupon.setPublic(isPublic);
        coupon.setStatus(status);
        coupon.setStartDate(OffsetDateTime.now().minusDays(1));
        coupon.setEndDate(OffsetDateTime.now().plusDays(10));
        return couponRepository.save(coupon);
    }

    private Product createProduct(User seller, BigDecimal price) {
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
        product.setName("Product " + UUID.randomUUID());
        product.setSlug("prod-" + UUID.randomUUID());
        product.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        product.setPrice(price);
        product.setStockQuantity(50);
        product.setSeller(seller);
        product.setCategory(category);
        product.setBrand(brand);
        product.setActive(true);
        product = productRepository.save(product);
        createdProductIds.add(product.getId());
        return product;
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
