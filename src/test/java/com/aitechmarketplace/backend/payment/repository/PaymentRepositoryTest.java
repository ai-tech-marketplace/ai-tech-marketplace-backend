package com.aitechmarketplace.backend.payment.repository;

import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import com.aitechmarketplace.backend.order.entity.Payment;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.order.entity.PaymentStatus;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.order.repository.PaymentRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail("payuser_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        testUser.setPasswordHash("hashedpassword");
        testUser.setFullName("Payment Test User");
        testUser.setPhone("0901234567");
        testUser = userRepository.save(testUser);

        testOrder = new Order();
        testOrder.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8));
        testOrder.setUser(testUser);
        testOrder.setStatus(OrderStatus.PENDING);
        testOrder.setPaymentMethod(PaymentMethod.VNPAY);
        testOrder.setPaymentStatus(PaymentStatus.PENDING);
        testOrder.setSubtotal(new BigDecimal("500000.00"));
        testOrder.setTotalAmount(new BigDecimal("500000.00"));
        testOrder.setShippingRecipientName("Recipient");
        testOrder.setShippingPhone("0901234567");
        testOrder.setShippingAddress("123 Tech Street");
        testOrder = orderRepository.save(testOrder);
    }

    @AfterEach
    void tearDown() {
        paymentRepository.deleteAll();
        if (testOrder != null && testOrder.getId() != null) {
            orderRepository.deleteById(testOrder.getId());
        }
        if (testUser != null && testUser.getId() != null) {
            userRepository.deleteById(testUser.getId());
        }
    }

    @Test
    void testSaveAndFindByTransactionReference_Vnpay() {
        String txnRef = "ORD_VNPAY_" + UUID.randomUUID().toString().substring(0, 8);

        Payment payment = new Payment(testOrder, PaymentMethod.VNPAY, new BigDecimal("500000.00"));
        payment.setTransactionReference(txnRef);
        payment.setProvider("VNPAY");
        payment.setProviderTransactionId("VNP12345678");
        payment.setProviderResponse("{\"vnp_ResponseCode\":\"00\"}");
        payment.setExpiresAt(OffsetDateTime.now().plusMinutes(15));
        payment = paymentRepository.save(payment);

        Optional<Payment> found = paymentRepository.findByTransactionReference(txnRef);
        assertThat(found).isPresent();
        Payment retrieved = found.get();
        assertThat(retrieved.getPaymentMethod()).isEqualTo(PaymentMethod.VNPAY);
        assertThat(retrieved.getProvider()).isEqualTo("VNPAY");
        assertThat(retrieved.getProviderTransactionId()).isEqualTo("VNP12345678");
        assertThat(retrieved.getProviderResponse()).contains("00");
        assertThat(retrieved.getExpiresAt()).isNotNull();
    }

    @Test
    void testFindTopByOrderIdOrderByCreatedAtDesc() {
        Payment payment1 = new Payment(testOrder, PaymentMethod.MOMO, new BigDecimal("500000.00"));
        payment1.setTransactionReference("MOMO_REF_1_" + UUID.randomUUID().toString().substring(0, 6));
        payment1.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment1);

        Payment payment2 = new Payment(testOrder, PaymentMethod.STRIPE, new BigDecimal("500000.00"));
        payment2.setTransactionReference("STRIPE_REF_2_" + UUID.randomUUID().toString().substring(0, 6));
        payment2.setStatus(PaymentStatus.PAID_AFTER_CANCELLED);
        payment2.setPaidAt(OffsetDateTime.now());
        paymentRepository.save(payment2);

        Optional<Payment> top = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(testOrder.getId());
        assertThat(top).isPresent();
        assertThat(top.get().getTransactionReference()).isEqualTo(payment2.getTransactionReference());
        assertThat(top.get().getPaymentMethod()).isEqualTo(PaymentMethod.STRIPE);
        assertThat(top.get().getStatus()).isEqualTo(PaymentStatus.PAID_AFTER_CANCELLED);
        assertThat(top.get().getPaidAt()).isNotNull();
    }
}
