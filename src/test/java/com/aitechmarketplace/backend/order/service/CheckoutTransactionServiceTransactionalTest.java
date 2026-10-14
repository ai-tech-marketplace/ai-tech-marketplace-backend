package com.aitechmarketplace.backend.order.service;

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
import com.aitechmarketplace.backend.order.dto.OrderCreateRequest;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.order.repository.OrderItemRepository;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.order.repository.PaymentRepository;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

/**
 * Integration test proving {@link CheckoutTransactionService#execute} actually runs
 * inside a Spring {@code @Transactional} boundary.
 *
 * <p>The test verifies that if the execution throws after a DB write, the write is
 * rolled back — which is only possible if {@code @Transactional} is active. If the
 * annotation were missing, the partial write would persist.
 *
 * <p>Uses {@code @MockitoSpyBean} (not {@code @MockitoBean}) so the real
 * {@link PaymentRepository} bean stays alive: cleanup's {@code deleteAll()} still
 * hits the real database, and only the stubbed {@code save()} call throws.
 */
@SpringBootTest
class CheckoutTransactionServiceTransactionalTest {

    @Autowired
    private CheckoutTransactionService checkoutTransactionService;

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Spy on the real PaymentRepository so we can force a RuntimeException AFTER the
    // Order row is written, proving that @Transactional rolls back the whole transaction.
    // @MockitoSpyBean wraps the real bean — deleteAll() in cleanup still works.
    @MockitoSpyBean
    private PaymentRepository paymentRepository;

    private final List<Long> cleanup_userIds = new ArrayList<>();
    private final List<Long> cleanup_productIds = new ArrayList<>();
    private final List<Long> cleanup_categoryIds = new ArrayList<>();
    private final List<Long> cleanup_brandIds = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void cleanup() {
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        addressRepository.deleteAll();
        cleanup_productIds.forEach(id -> { if (productRepository.existsById(id)) productRepository.deleteById(id); });
        cleanup_productIds.clear();
        cleanup_categoryIds.forEach(id -> { if (categoryRepository.existsById(id)) categoryRepository.deleteById(id); });
        cleanup_categoryIds.clear();
        cleanup_brandIds.forEach(id -> { if (brandRepository.existsById(id)) brandRepository.deleteById(id); });
        cleanup_brandIds.clear();
        cleanup_userIds.forEach(id -> { if (userRepository.existsById(id)) userRepository.deleteById(id); });
        cleanup_userIds.clear();
    }

    /**
     * Proves {@code @Transactional} is active on {@code execute()}:
     * if a runtime exception is thrown after the Order is written to the DB,
     * the whole transaction rolls back — no orphaned Order row remains.
     */
    @Test
    void execute_rollsBackOrderSave_whenPaymentSaveThrows() {
        // Arrange
        User user = createUser();
        Address address = createAddress(user);
        Product product = createProduct(user, new BigDecimal("50000.00"), 5);
        Cart cart = cartRepository.save(new Cart(user));
        cartItemRepository.save(new CartItem(cart, product, 1));

        // Force an exception AFTER order is saved but during payment save
        doThrow(new RuntimeException("Simulated payment failure"))
                .when(paymentRepository).save(org.mockito.ArgumentMatchers.any());

        OrderCreateRequest request = new OrderCreateRequest(address.getId(), PaymentMethod.COD, null, null);

        // Act + Assert: exception propagates
        assertThatThrownBy(() -> checkoutTransactionService.execute(user.getId(), request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated payment failure");

        // Assert: @Transactional rolled back — no order persisted
        assertThat(orderRepository.findByUserId(user.getId(),
                org.springframework.data.domain.Pageable.ofSize(10)).getTotalElements())
                .isZero();

        // Assert: stock was NOT permanently deducted (rolled back)
        Product refreshed = productRepository.findById(product.getId()).orElseThrow();
        assertThat(refreshed.getStockQuantity()).isEqualTo(5);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private User createUser() {
        User user = new User();
        user.setEmail("cts-test-" + UUID.randomUUID() + "@example.com");
        user.setFullName("CTS Test User");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user = userRepository.save(user);
        cleanup_userIds.add(user.getId());
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

    private Product createProduct(User seller, BigDecimal price, int stock) {
        Category category = new Category();
        category.setName("Cat-" + UUID.randomUUID());
        category.setSlug("cat-" + UUID.randomUUID());
        category = categoryRepository.save(category);
        cleanup_categoryIds.add(category.getId());

        Brand brand = new Brand();
        brand.setName("Brand-" + UUID.randomUUID());
        brand.setSlug("brand-" + UUID.randomUUID());
        brand = brandRepository.save(brand);
        cleanup_brandIds.add(brand.getId());

        Product product = new Product();
        product.setName("Product-" + UUID.randomUUID());
        product.setSlug("prod-" + UUID.randomUUID());
        product.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        product.setPrice(price);
        product.setStockQuantity(stock);
        product.setCategory(category);
        product.setBrand(brand);
        product.setSeller(seller);
        product.setActive(true);
        product = productRepository.save(product);
        cleanup_productIds.add(product.getId());
        return product;
    }
}
