package com.aitechmarketplace.backend.order.scheduler;

import com.aitechmarketplace.backend.brand.entity.Brand;
import com.aitechmarketplace.backend.brand.repository.BrandRepository;
import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.coupon.entity.Coupon;
import com.aitechmarketplace.backend.coupon.entity.CouponUsage;
import com.aitechmarketplace.backend.coupon.entity.DiscountType;
import com.aitechmarketplace.backend.coupon.repository.CouponRepository;
import com.aitechmarketplace.backend.coupon.repository.CouponUsageRepository;
import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.OrderItem;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import com.aitechmarketplace.backend.order.entity.Payment;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.order.entity.PaymentStatus;
import com.aitechmarketplace.backend.order.repository.OrderItemRepository;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.order.repository.PaymentRepository;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import com.aitechmarketplace.backend.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderExpirationSchedulerTest {

    @Autowired
    private OrderExpirationScheduler orderExpirationScheduler;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

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
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void cancelExpiredOrders_shouldCancelExpiredPendingOrderAndRestockAndReleaseCoupon() {
        User user = createUser();
        Product product = createProduct(user, 10);
        Coupon coupon = createCoupon("TEST15M", 5);

        // Create expired order (20 minutes ago)
        Order expiredOrder = createOrder(user, PaymentMethod.VNPAY, new BigDecimal("500000.00"));
        OrderItem item = new OrderItem(expiredOrder, product, product.getName(), "SKU-1", 2, product.getPrice(), product.getPrice().multiply(BigDecimal.valueOf(2)));
        orderItemRepository.save(item);

        // Reduce product stock to simulate order checkout reservation (10 - 2 = 8)
        product.setStockQuantity(8);
        productRepository.save(product);

        // Record coupon usage
        CouponUsage usage = new CouponUsage();
        usage.setUser(user);
        usage.setCoupon(coupon);
        usage.setOrderId(expiredOrder.getId());
        usage.setDiscountAmount(new BigDecimal("50000.00"));
        usage.setStatus("USED");
        couponUsageRepository.save(usage);

        // Record pending payment
        Payment payment = new Payment(expiredOrder, PaymentMethod.VNPAY, new BigDecimal("500000.00"));
        payment.setTransactionReference(expiredOrder.getOrderNumber() + "_PAY_OLD");
        paymentRepository.save(payment);

        // Manually update created_at to 20 minutes ago
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?",
                OffsetDateTime.now().minusMinutes(20), expiredOrder.getId());

        // Create non-expired order (5 minutes ago)
        Order recentOrder = createOrder(user, PaymentMethod.VNPAY, new BigDecimal("300000.00"));
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?",
                OffsetDateTime.now().minusMinutes(5), recentOrder.getId());

        // Create expired COD order (20 minutes ago)
        Order codOrder = createOrder(user, PaymentMethod.COD, new BigDecimal("200000.00"));
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?",
                OffsetDateTime.now().minusMinutes(20), codOrder.getId());

        // Execute scheduled job
        orderExpirationScheduler.cancelExpiredOrders();

        // Verify expired online order cancelled
        Order cancelledOrder = orderRepository.findById(expiredOrder.getId()).orElseThrow();
        assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // Verify product stock restored (8 + 2 = 10)
        Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloadedProduct.getStockQuantity()).isEqualTo(10);

        // Verify coupon usage released
        CouponUsage reloadedUsage = couponUsageRepository.findByOrderId(expiredOrder.getId()).orElseThrow();
        assertThat(reloadedUsage.getStatus()).isEqualTo("RELEASED");
        Coupon reloadedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(reloadedCoupon.getUsedCount()).isEqualTo(4);

        // Verify payment marked CANCELLED
        Payment reloadedPayment = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(expiredOrder.getId()).orElseThrow();
        assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        // Verify non-expired order is still PENDING
        Order untouchedRecentOrder = orderRepository.findById(recentOrder.getId()).orElseThrow();
        assertThat(untouchedRecentOrder.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Verify COD order is still PENDING (not cancelled by 15-minute online payment timeout)
        Order untouchedCodOrder = orderRepository.findById(codOrder.getId()).orElseThrow();
        assertThat(untouchedCodOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    private User createUser() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = "buyer_" + unique + "@example.com";
        String passwordHash = passwordEncoder.encode("Password123!");
        User user = userService.register(email, passwordHash, "Buyer " + unique, null);
        createdUserIds.add(user.getId());
        return user;
    }

    private Product createProduct(User seller, int stockQuantity) {
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
        product.setSlug("prod-" + UUID.randomUUID());
        product.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        product.setPrice(new BigDecimal("250000.00"));
        product.setStockQuantity(stockQuantity);
        product.setActive(true);
        product = productRepository.save(product);
        createdProductIds.add(product.getId());
        return product;
    }

    private Coupon createCoupon(String code, int usedCount) {
        Coupon coupon = new Coupon();
        coupon.setCode(code + "_" + UUID.randomUUID().toString().substring(0, 6));
        coupon.setName("Coupon " + code);
        coupon.setDiscountType(DiscountType.FIXED_AMOUNT);
        coupon.setDiscountValue(new BigDecimal("50000.00"));
        coupon.setMinOrderValue(BigDecimal.ZERO);
        coupon.setUsageLimit(100);
        coupon.setUsedCount(usedCount);
        coupon.setUsageLimitPerUser(1);
        coupon.setPublic(true);
        coupon.setStatus("ACTIVE");
        coupon.setStartDate(OffsetDateTime.now().minusDays(1));
        coupon.setEndDate(OffsetDateTime.now().plusDays(10));
        return couponRepository.save(coupon);
    }

    private Order createOrder(User user, PaymentMethod paymentMethod, BigDecimal totalAmount) {
        Order order = new Order();
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setPaymentMethod(paymentMethod);
        order.setSubtotal(totalAmount);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setShippingFee(BigDecimal.ZERO);
        order.setTotalAmount(totalAmount);
        order.setShippingRecipientName("Recipient Test");
        order.setShippingPhone("0909000111");
        order.setShippingAddress("456 Street, Danang");
        order.setShippingCity("Danang");
        return orderRepository.save(order);
    }
}
