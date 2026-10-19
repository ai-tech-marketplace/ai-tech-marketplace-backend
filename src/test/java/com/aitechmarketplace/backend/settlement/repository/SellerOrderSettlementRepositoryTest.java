package com.aitechmarketplace.backend.settlement.repository;

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
import com.aitechmarketplace.backend.settlement.entity.SettlementStatus;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SellerOrderSettlementRepositoryTest {

    @Autowired
    private SellerOrderSettlementRepository repository;

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

    @AfterEach
    void tearDown() {
        repository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldSaveAndFindSettlements() {
        User seller = new User();
        seller.setEmail("seller-" + UUID.randomUUID() + "@example.com");
        seller.setPasswordHash("hash");
        seller.setFullName("Seller User");
        seller = userRepository.save(seller);

        User buyer = new User();
        buyer.setEmail("buyer-" + UUID.randomUUID() + "@example.com");
        buyer.setPasswordHash("hash");
        buyer.setFullName("Buyer User");
        buyer = userRepository.save(buyer);

        Category category = new Category();
        category.setName("Category " + UUID.randomUUID());
        category.setSlug("cat-" + UUID.randomUUID());
        category = categoryRepository.save(category);

        Product product = new Product();
        product.setName("Product " + UUID.randomUUID());
        product.setSlug("prod-" + UUID.randomUUID());
        product.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        product.setPrice(new BigDecimal("1000000.00"));
        product.setStockQuantity(10);
        product.setSeller(seller);
        product.setCategory(category);
        product.setActive(true);
        product = productRepository.save(product);

        Order order = new Order();
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8));
        order.setUser(buyer);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setSubtotal(new BigDecimal("1000000.00"));
        order.setTotalAmount(new BigDecimal("1000000.00"));
        order.setShippingRecipientName("Buyer");
        order.setShippingPhone("0123456789");
        order.setShippingAddress("123 Street");
        order = orderRepository.save(order);

        OrderItem orderItem = new OrderItem(order, product, product.getName(), product.getSku(), 1, new BigDecimal("1000000.00"), new BigDecimal("1000000.00"));
        orderItem = orderItemRepository.save(orderItem);

        SellerOrderSettlement settlement = new SellerOrderSettlement(
                order.getId(), orderItem.getId(), seller.getId(),
                new BigDecimal("1000000.00"),
                new BigDecimal("0.0500"),
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                new BigDecimal("950000.00")
        );
        SellerOrderSettlement saved = repository.save(settlement);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.PENDING);

        List<SellerOrderSettlement> byOrder = repository.findByOrderId(order.getId());
        assertThat(byOrder).hasSize(1);
        assertThat(byOrder.get(0).getSellerNetAmount()).isEqualByComparingTo("950000.00");

        Page<SellerOrderSettlement> bySellerStatus = repository.findBySellerIdAndStatus(
                seller.getId(), SettlementStatus.PENDING, PageRequest.of(0, 10)
        );
        assertThat(bySellerStatus.getTotalElements()).isEqualTo(1);
    }
}
