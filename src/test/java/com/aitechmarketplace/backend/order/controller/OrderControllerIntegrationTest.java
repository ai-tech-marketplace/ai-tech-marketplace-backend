package com.aitechmarketplace.backend.order.controller;

import com.aitechmarketplace.backend.address.entity.Address;
import com.aitechmarketplace.backend.address.repository.AddressRepository;
import com.aitechmarketplace.backend.brand.entity.Brand;
import com.aitechmarketplace.backend.brand.repository.BrandRepository;
import com.aitechmarketplace.backend.cart.entity.Cart;
import com.aitechmarketplace.backend.cart.entity.CartItem;
import com.aitechmarketplace.backend.cart.repository.CartItemRepository;
import com.aitechmarketplace.backend.cart.repository.CartRepository;
import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.coupon.entity.Coupon;
import com.aitechmarketplace.backend.coupon.entity.CouponUsage;
import com.aitechmarketplace.backend.coupon.entity.DiscountType;
import com.aitechmarketplace.backend.coupon.repository.CouponRepository;
import com.aitechmarketplace.backend.coupon.repository.CouponUsageRepository;
import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import com.aitechmarketplace.backend.order.repository.OrderItemRepository;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.order.repository.PaymentRepository;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

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
    private CouponRepository couponRepository;

    @Autowired
    private CouponUsageRepository couponUsageRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

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
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        couponRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        addressRepository.deleteAll();

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
    void createOrder_shouldSucceed() throws Exception {
        User user = createUser();
        String token = login(user.getEmail());

        Address address = createAddress(user);
        Product product = createProduct(user, new BigDecimal("5000000.00"), 10);

        Cart cart = cartRepository.findByUserId(user.getId()).orElseGet(() -> cartRepository.save(new Cart(user)));
        cartItemRepository.save(new CartItem(cart, product, 2)); // Subtotal: 10,000,000

        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "addressId": %d,
                                    "paymentMethod": "COD",
                                    "notes": "Handle with care"
                                }
                                """.formatted(address.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderNumber", startsWith("ORD-")))
                .andExpect(jsonPath("$.data.status", is("PENDING")))
                .andExpect(jsonPath("$.data.paymentMethod", is("COD")))
                .andExpect(jsonPath("$.data.paymentStatus", is("PENDING")))
                .andExpect(jsonPath("$.data.subtotal", is(10000000.0)))
                .andExpect(jsonPath("$.data.shippingFee", is(30000.0)))
                .andExpect(jsonPath("$.data.discountAmount", is(0)))
                .andExpect(jsonPath("$.data.totalAmount", is(10030000.0)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].productName", is(product.getName())))
                .andExpect(jsonPath("$.data.shippingAddress.recipientName", is(address.getRecipientName())))
                .andReturn().getResponse().getContentAsString();

        // Stock must be decremented: 10 - 2 = 8
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(8);

        // Cart items must be cleared
        List<CartItem> remainingCartItems = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId());
        assertThat(remainingCartItems).isEmpty();
    }

    @Test
    void createOrder_withCoupon_shouldDeductDiscountAndRecordCouponUsage() throws Exception {
        User user = createUser();
        String token = login(user.getEmail());

        Address address = createAddress(user);
        Product product = createProduct(user, new BigDecimal("10000000.00"), 5);

        Cart cart = cartRepository.findByUserId(user.getId()).orElseGet(() -> cartRepository.save(new Cart(user)));
        cartItemRepository.save(new CartItem(cart, product, 1)); // Subtotal: 10,000,000

        Coupon coupon = createCoupon("OFF10", "10% off", DiscountType.PERCENTAGE, new BigDecimal("10.00"), new BigDecimal("500000.00"));

        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "addressId": %d,
                                    "paymentMethod": "VNPAY",
                                    "couponCode": "OFF10"
                                }
                                """.formatted(address.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.subtotal", is(10000000.0)))
                .andExpect(jsonPath("$.data.discountAmount", is(500000.0)))
                .andExpect(jsonPath("$.data.totalAmount", is(9530000.0))) // 10M + 30k - 500k
                .andExpect(jsonPath("$.data.paymentUrl", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        long orderId = json.path("data").path("id").asLong();

        // Coupon usage must be recorded
        CouponUsage usage = couponUsageRepository.findByOrderId(orderId).orElseThrow();
        assertThat(usage.getStatus()).isEqualTo("USED");
        assertThat(usage.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));

        Coupon updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getUsedCount()).isEqualTo(1);
    }

    @Test
    void getUserOrders_andGetOrderById_shouldReturnOrders() throws Exception {
        User user1 = createUser();
        String token1 = login(user1.getEmail());

        User user2 = createUser();
        String token2 = login(user2.getEmail());

        Address address = createAddress(user1);
        Product product = createProduct(user1, new BigDecimal("2000000.00"), 10);

        Cart cart = cartRepository.findByUserId(user1.getId()).orElseGet(() -> cartRepository.save(new Cart(user1)));
        cartItemRepository.save(new CartItem(cart, product, 1));

        String res = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "addressId": %d,
                                    "paymentMethod": "COD"
                                }
                                """.formatted(address.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long orderId = objectMapper.readTree(res).path("data").path("id").asLong();

        // User 1 lists their orders
        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id", is((int) orderId)));

        // User 1 retrieves order detail
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is((int) orderId)));

        // User 2 tries to access User 1's order -> 404 NOT FOUND
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("ORDER_NOT_FOUND")));
    }

    @Test
    void cancelOrder_shouldRestoreStockAndCoupon() throws Exception {
        User user = createUser();
        String token = login(user.getEmail());

        Address address = createAddress(user);
        Product product = createProduct(user, new BigDecimal("3000000.00"), 5);

        Cart cart = cartRepository.findByUserId(user.getId()).orElseGet(() -> cartRepository.save(new Cart(user)));
        cartItemRepository.save(new CartItem(cart, product, 2));

        Coupon coupon = createCoupon("CANCELCOUPON", "Voucher", DiscountType.FIXED_AMOUNT, new BigDecimal("100000.00"), null);

        String createRes = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "addressId": %d,
                                    "paymentMethod": "COD",
                                    "couponCode": "CANCELCOUPON"
                                }
                                """.formatted(address.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long orderId = objectMapper.readTree(createRes).path("data").path("id").asLong();

        // Product stock is currently 3 (5 - 2)
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(3);

        // Cancel order
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        // Product stock restored to 5
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);

        // Coupon released
        CouponUsage usage = couponUsageRepository.findByOrderId(orderId).orElseThrow();
        assertThat(usage.getStatus()).isEqualTo("RELEASED");
        assertThat(usage.getReleasedAt()).isNotNull();

        Coupon updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getUsedCount()).isEqualTo(0);
    }

    private User createUser() {
        String email = "order-user-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);
        User user = userService.register(email, passwordHash, "Order User", null);
        createdUserIds.add(user.getId());
        return user;
    }

    private Address createAddress(User user) {
        Address address = new Address();
        address.setUser(user);
        address.setRecipientName("Alice Smith");
        address.setPhone("0987654321");
        address.setAddressLine("456 Market St");
        address.setCity("Ho Chi Minh City");
        address.setCountry("Vietnam");
        address.setDefault(true);
        return addressRepository.save(address);
    }

    private Product createProduct(User seller, BigDecimal price, int stockQuantity) {
        Category category = new Category();
        category.setName("Cat " + UUID.randomUUID());
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
        product.setStockQuantity(stockQuantity);
        product.setSeller(seller);
        product.setCategory(category);
        product.setBrand(brand);
        product.setActive(true);
        product = productRepository.save(product);
        createdProductIds.add(product.getId());
        return product;
    }

    private Coupon createCoupon(String code, String name, DiscountType type, BigDecimal value, BigDecimal maxDiscount) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setName(name);
        coupon.setDiscountType(type);
        coupon.setDiscountValue(value);
        coupon.setMaxDiscountAmount(maxDiscount);
        coupon.setMinOrderValue(BigDecimal.ZERO);
        coupon.setUsageLimit(100);
        coupon.setUsedCount(0);
        coupon.setUsageLimitPerUser(1);
        coupon.setPublic(true);
        coupon.setStatus("ACTIVE");
        coupon.setStartDate(OffsetDateTime.now().minusDays(1));
        coupon.setEndDate(OffsetDateTime.now().plusDays(10));
        return couponRepository.save(coupon);
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
