package com.aitechmarketplace.backend.order;

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
import com.aitechmarketplace.backend.coupon.repository.CouponRepository;
import com.aitechmarketplace.backend.coupon.repository.CouponUsageRepository;
import com.aitechmarketplace.backend.event.DomainEventPublisher;
import com.aitechmarketplace.backend.event.EventTopics;
import com.aitechmarketplace.backend.event.consumer.OrderNotificationConsumer;
import com.aitechmarketplace.backend.order.dto.OrderCreateRequest;
import com.aitechmarketplace.backend.order.dto.OrderResponse;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.order.repository.OrderItemRepository;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.order.repository.PaymentRepository;
import com.aitechmarketplace.backend.order.service.OrderService;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import com.aitechmarketplace.backend.user.service.UserService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@EmbeddedKafka(partitions = 1, topics = {EventTopics.ORDERS})
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class OrderCheckoutIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private OrderService orderService;

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

    @MockitoSpyBean
    private DomainEventPublisher domainEventPublisher;

    @MockitoSpyBean
    private OrderNotificationConsumer notificationConsumer;

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
    void checkout_deductsStock_andPublishesEvent() {
        User user = createUser();
        Address address = createAddress(user);
        Product product = createProduct(user, new BigDecimal("100000.00"), 10);

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(new Cart(user)));
        cartItemRepository.save(new CartItem(cart, product, 2));

        OrderCreateRequest request = new OrderCreateRequest(
                address.getId(),
                PaymentMethod.COD,
                null,
                "Integration test notes"
        );

        OrderResponse response = orderService.createOrder(user.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.orderNumber()).startsWith("ORD-");

        // Verify pessimistic lock / DB update: stock deducted from 10 to 8
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(8);

        // Verify domain event published after commit
        verify(domainEventPublisher, timeout(10000)).publishOrderCreated(
                argThat(event -> event.orderNumber().equals(response.orderNumber())
                        && event.userId().equals(user.getId()))
        );

        // Verify Kafka consumer receives the event — key is userId (user-level partitioning)
        verify(notificationConsumer, timeout(10000)).handleOrderEvent(
                argThat(raw -> {
                    if (raw instanceof ConsumerRecord<?, ?> cr) {
                        return cr.key() != null && cr.key().equals(user.getId().toString());
                    }
                    return false;
                })
        );
    }

    @Test
    void concurrentCheckout_sameProduct_noOversell() throws Exception {
        User seller = createUser();
        Product product = createProduct(seller, new BigDecimal("200000.00"), 1);

        User buyer1 = createUser();
        Address address1 = createAddress(buyer1);
        Cart cart1 = cartRepository.findByUserId(buyer1.getId())
                .orElseGet(() -> cartRepository.save(new Cart(buyer1)));
        cartItemRepository.save(new CartItem(cart1, product, 1));

        User buyer2 = createUser();
        Address address2 = createAddress(buyer2);
        Cart cart2 = cartRepository.findByUserId(buyer2.getId())
                .orElseGet(() -> cartRepository.save(new Cart(buyer2)));
        cartItemRepository.save(new CartItem(cart2, product, 1));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        Runnable task1 = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                orderService.createOrder(buyer1.getId(), new OrderCreateRequest(address1.getId(), PaymentMethod.COD, null, null));
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
            }
        };

        Runnable task2 = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                orderService.createOrder(buyer2.getId(), new OrderCreateRequest(address2.getId(), PaymentMethod.COD, null, null));
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
            }
        };

        executor.submit(task1);
        executor.submit(task2);

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown(); // trigger both simultaneously

        executor.shutdown();
        assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        // One must succeed, one must fail due to out-of-stock (pessimistic locking)
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        Product finalProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(finalProduct.getStockQuantity()).isEqualTo(0);
    }

    private User createUser() {
        String email = "checkout-user-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);
        User user = userService.register(email, passwordHash, "Checkout User", null);
        createdUserIds.add(user.getId());
        return user;
    }

    private Address createAddress(User user) {
        Address address = new Address();
        address.setUser(user);
        address.setRecipientName("Test Recipient");
        address.setPhone("0987654321");
        address.setAddressLine("123 Test St");
        address.setCity("Hanoi");
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
}
