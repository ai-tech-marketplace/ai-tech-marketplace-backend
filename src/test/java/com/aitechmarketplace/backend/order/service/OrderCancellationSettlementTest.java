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
import com.aitechmarketplace.backend.commission.repository.CommissionSettingRepository;
import com.aitechmarketplace.backend.event.EventTopics;
import com.aitechmarketplace.backend.order.dto.OrderCreateRequest;
import com.aitechmarketplace.backend.order.dto.OrderResponse;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.order.repository.OrderItemRepository;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.order.repository.PaymentRepository;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.settlement.entity.SellerOrderSettlement;
import com.aitechmarketplace.backend.settlement.entity.SettlementStatus;
import com.aitechmarketplace.backend.settlement.repository.SellerOrderSettlementRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EmbeddedKafka(partitions = 1, topics = {EventTopics.ORDERS})
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class OrderCancellationSettlementTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private SellerOrderSettlementRepository settlementRepository;

    @Autowired
    private CommissionSettingRepository commissionSettingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdBrandIds = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void cleanup() {
        settlementRepository.deleteAll();
        commissionSettingRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        addressRepository.deleteAll();

        createdProductIds.forEach(id -> {
            if (productRepository.existsById(id)) productRepository.deleteById(id);
        });
        createdProductIds.clear();

        createdCategoryIds.forEach(id -> {
            if (categoryRepository.existsById(id)) categoryRepository.deleteById(id);
        });
        createdCategoryIds.clear();

        createdBrandIds.forEach(id -> {
            if (brandRepository.existsById(id)) brandRepository.deleteById(id);
        });
        createdBrandIds.clear();

        createdUserIds.forEach(id -> {
            if (userRepository.existsById(id)) userRepository.deleteById(id);
        });
        createdUserIds.clear();
    }

    @Test
    void cancelOrder_shouldTransitionAllSettlementsToCancelled() {
        User buyer = createUser("Buyer");
        User seller = createUser("Seller");
        Address address = createAddress(buyer);

        Category category = new Category();
        category.setName("Category " + UUID.randomUUID());
        category.setSlug("cat-" + UUID.randomUUID());
        category = categoryRepository.save(category);
        createdCategoryIds.add(category.getId());

        Product product = createProduct(seller, category, new BigDecimal("500000.00"), 10);

        Cart cart = cartRepository.save(new Cart(buyer));
        cartItemRepository.save(new CartItem(cart, product, 1));

        OrderCreateRequest request = new OrderCreateRequest(address.getId(), PaymentMethod.COD, null, "Test notes");
        OrderResponse created = orderService.createOrder(buyer.getId(), request);

        // Verify settlements exist and are PENDING
        List<SellerOrderSettlement> initialSettlements = settlementRepository.findByOrderId(created.id());
        assertThat(initialSettlements).hasSize(1);
        assertThat(initialSettlements.get(0).getStatus()).isEqualTo(SettlementStatus.PENDING);

        // Cancel order
        OrderResponse cancelled = orderService.cancelOrder(created.id(), buyer.getId(), false);
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);

        // Verify settlements transitioned to CANCELLED
        List<SellerOrderSettlement> updatedSettlements = settlementRepository.findByOrderId(created.id());
        assertThat(updatedSettlements).hasSize(1);
        assertThat(updatedSettlements.get(0).getStatus()).isEqualTo(SettlementStatus.CANCELLED);
    }

    private User createUser(String name) {
        User user = new User();
        user.setEmail("user-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("hash");
        user.setFullName(name);
        user = userRepository.save(user);
        createdUserIds.add(user.getId());
        return user;
    }

    private Address createAddress(User user) {
        Address address = new Address();
        address.setUser(user);
        address.setRecipientName("Recipient");
        address.setPhone("0987654321");
        address.setAddressLine("123 Street");
        address.setCity("Hanoi");
        address.setCountry("Vietnam");
        address.setDefault(true);
        return addressRepository.save(address);
    }

    private Product createProduct(User seller, Category category, BigDecimal price, int stock) {
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
        product.setStockQuantity(stock);
        product.setSeller(seller);
        product.setCategory(category);
        product.setBrand(brand);
        product.setActive(true);
        product = productRepository.save(product);
        createdProductIds.add(product.getId());
        return product;
    }
}
