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
import com.aitechmarketplace.backend.commission.entity.CommissionScope;
import com.aitechmarketplace.backend.commission.entity.CommissionSetting;
import com.aitechmarketplace.backend.commission.repository.CommissionSettingRepository;
import com.aitechmarketplace.backend.event.EventTopics;
import com.aitechmarketplace.backend.order.dto.OrderCreateRequest;
import com.aitechmarketplace.backend.order.dto.OrderResponse;
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
class CheckoutTransactionServiceSettlementTest {

    @Autowired
    private CheckoutTransactionService checkoutTransactionService;

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
    void execute_shouldPersistSellerOrderSettlementsWithCorrectRates() {
        User buyer = createUser("Buyer");
        User sellerA = createUser("Seller A");
        User sellerB = createUser("Seller B");

        Address address = createAddress(buyer);

        Category category = new Category();
        category.setName("Electronics " + UUID.randomUUID());
        category.setSlug("elec-" + UUID.randomUUID());
        category = categoryRepository.save(category);
        createdCategoryIds.add(category.getId());

        // Custom rate for Seller A: 2.0%
        CommissionSetting sellerASetting = new CommissionSetting(
                CommissionScope.SELLER, sellerA.getId(), new BigDecimal("0.0200"), "Seller A negotiated rate", true
        );
        commissionSettingRepository.save(sellerASetting);

        // Seller B has no specific rate -> fallback to 5.0%

        Product prodA = createProduct(sellerA, category, new BigDecimal("1000000.00"), 10);
        Product prodB = createProduct(sellerB, category, new BigDecimal("500000.00"), 10);

        Cart cart = cartRepository.save(new Cart(buyer));
        cartItemRepository.save(new CartItem(cart, prodA, 1)); // 1,000,000
        cartItemRepository.save(new CartItem(cart, prodB, 2)); // 1,000,000

        OrderCreateRequest request = new OrderCreateRequest(address.getId(), PaymentMethod.COD, null, "Test notes");

        OrderResponse orderResponse = checkoutTransactionService.execute(buyer.getId(), request);

        assertThat(orderResponse).isNotNull();

        List<SellerOrderSettlement> settlements = settlementRepository.findByOrderId(orderResponse.id());
        assertThat(settlements).hasSize(2);

        SellerOrderSettlement settlementA = settlements.stream()
                .filter(s -> s.getSellerId().equals(sellerA.getId()))
                .findFirst().orElseThrow();
        assertThat(settlementA.getItemSubtotal()).isEqualByComparingTo("1000000.00");
        assertThat(settlementA.getCommissionRate()).isEqualByComparingTo("0.0200");
        assertThat(settlementA.getCommissionAmount()).isEqualByComparingTo("20000.00");
        assertThat(settlementA.getSellerNetAmount()).isEqualByComparingTo("980000.00");
        assertThat(settlementA.getStatus()).isEqualTo(SettlementStatus.PENDING);

        SellerOrderSettlement settlementB = settlements.stream()
                .filter(s -> s.getSellerId().equals(sellerB.getId()))
                .findFirst().orElseThrow();
        assertThat(settlementB.getItemSubtotal()).isEqualByComparingTo("1000000.00");
        assertThat(settlementB.getCommissionRate()).isEqualByComparingTo("0.0500");
        assertThat(settlementB.getCommissionAmount()).isEqualByComparingTo("50000.00");
        assertThat(settlementB.getSellerNetAmount()).isEqualByComparingTo("950000.00");
        assertThat(settlementB.getStatus()).isEqualTo(SettlementStatus.PENDING);
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
