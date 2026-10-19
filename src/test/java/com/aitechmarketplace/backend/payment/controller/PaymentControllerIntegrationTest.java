package com.aitechmarketplace.backend.payment.controller;

import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import com.aitechmarketplace.backend.order.entity.Payment;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.order.entity.PaymentStatus;
import com.aitechmarketplace.backend.order.repository.OrderItemRepository;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.order.repository.PaymentRepository;
import com.aitechmarketplace.backend.payment.config.MomoProperties;
import com.aitechmarketplace.backend.payment.config.StripeProperties;
import com.aitechmarketplace.backend.payment.config.VnpayProperties;
import com.aitechmarketplace.backend.payment.provider.momo.MomoUtils;
import com.aitechmarketplace.backend.payment.provider.vnpay.VnpayUtils;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import com.aitechmarketplace.backend.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private VnpayProperties vnpayProperties;

    @Autowired
    private MomoProperties momoProperties;

    @Autowired
    private StripeProperties stripeProperties;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void cleanup() {
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();

        createdUserIds.forEach(id -> {
            if (userRepository.existsById(id)) {
                userRepository.deleteById(id);
            }
        });
        createdUserIds.clear();
    }

    @Test
    void initiatePayment_vnpay_shouldReturnInitiateResponse() throws Exception {
        User user = createUser();
        String token = login(user.getEmail());

        Order order = createOrder(user, PaymentMethod.VNPAY, new BigDecimal("500000.00"));

        mockMvc.perform(
                post("/api/v1/payments/" + order.getId() + "/initiate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "paymentMethod": "VNPAY",
                                    "returnUrl": "http://localhost:3000/checkout/result"
                                }
                                """)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactionReference").value(startsWith(order.getOrderNumber() + "_PAY_")))
                .andExpect(jsonPath("$.data.paymentUrl").value(containsString("sandbox.vnpayment.vn")));

        List<Payment> payments = paymentRepository.findAll();
        assertThat(payments).hasSize(1);
        Payment payment = payments.get(0);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getTransactionReference()).startsWith(order.getOrderNumber() + "_PAY_");
    }

    @Test
    void initiatePayment_unauthorized_shouldReturn401() throws Exception {
        User user = createUser();
        Order order = createOrder(user, PaymentMethod.VNPAY, new BigDecimal("500000.00"));

        mockMvc.perform(
                post("/api/v1/payments/" + order.getId() + "/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "paymentMethod": "VNPAY"
                                }
                                """)
        )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void initiatePayment_forbiddenForOtherUserOrder_shouldReturn403() throws Exception {
        User owner = createUser();
        User attacker = createUser();
        String attackerToken = login(attacker.getEmail());

        Order order = createOrder(owner, PaymentMethod.VNPAY, new BigDecimal("500000.00"));

        mockMvc.perform(
                post("/api/v1/payments/" + order.getId() + "/initiate")
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "paymentMethod": "VNPAY"
                                }
                                """)
        )
                .andExpect(status().isForbidden());
    }

    @Test
    void handleVnpayIpn_validChecksum_shouldConfirmOrderAndPayment() throws Exception {
        User user = createUser();
        Order order = createOrder(user, PaymentMethod.VNPAY, new BigDecimal("500000.00"));

        Payment payment = new Payment(order, PaymentMethod.VNPAY, new BigDecimal("500000.00"));
        String txnRef = order.getOrderNumber() + "_PAY_" + System.currentTimeMillis();
        payment.setTransactionReference(txnRef);
        payment.setProvider("VNPAY");
        payment = paymentRepository.save(payment);

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "50000000"); // 500,000 * 100
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan don hang " + order.getOrderNumber());
        params.put("vnp_PayDate", "20261019153000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", vnpayProperties.getTmnCode());
        params.put("vnp_TransactionNo", "14567890");
        params.put("vnp_TxnRef", txnRef);

        String hashData = VnpayUtils.buildHashData(params);
        String secureHash = VnpayUtils.hmacSHA512(vnpayProperties.getHashSecret(), hashData);

        var requestBuilder = get("/api/v1/payments/vnpay/ipn");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            requestBuilder.param(entry.getKey(), entry.getValue());
        }
        requestBuilder.param("vnp_SecureHash", secureHash);

        mockMvc.perform(requestBuilder)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(updatedOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(updatedPayment.getProviderTransactionId()).isEqualTo("14567890");
    }

    @Test
    void handleVnpayIpn_invalidChecksum_shouldReturn97() throws Exception {
        mockMvc.perform(
                get("/api/v1/payments/vnpay/ipn")
                        .param("vnp_TxnRef", "ORD-UNKNOWN")
                        .param("vnp_SecureHash", "invalidhash")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"));
    }

    @Test
    void handleVnpayReturn_shouldRedirectToStorefront() throws Exception {
        mockMvc.perform(
                get("/api/v1/payments/vnpay/return")
                        .param("vnp_TxnRef", "ORD-12345_PAY_9999")
                        .param("vnp_ResponseCode", "00")
        )
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost:3000/checkout/result?orderNumber=ORD-12345&status=SUCCESS"));
    }

    @Test
    void handleMomoIpn_validSignature_shouldConfirmOrderAndPayment() throws Exception {
        User user = createUser();
        Order order = createOrder(user, PaymentMethod.MOMO, new BigDecimal("500000.00"));

        Payment payment = new Payment(order, PaymentMethod.MOMO, new BigDecimal("500000.00"));
        String txnRef = order.getOrderNumber() + "_PAY_" + System.currentTimeMillis();
        payment.setTransactionReference(txnRef);
        payment.setProvider("MOMO");
        payment = paymentRepository.save(payment);

        String partnerCode = momoProperties.getPartnerCode();
        String requestId = "REQ-" + System.currentTimeMillis();
        long amount = 500000L;
        String orderInfo = "Thanh toan don hang";
        String orderType = "momo_wallet";
        long transId = 2309192931L;
        int resultCode = 0;
        String message = "Successful.";
        String payType = "qr";
        long responseTime = 1729000050000L;
        String extraData = "";

        String signature = MomoUtils.buildIpnSignature(
                momoProperties.getAccessKey(),
                String.valueOf(amount),
                extraData,
                message,
                txnRef,
                orderInfo,
                orderType,
                partnerCode,
                payType,
                requestId,
                String.valueOf(responseTime),
                String.valueOf(resultCode),
                String.valueOf(transId),
                momoProperties.getSecretKey()
        );

        mockMvc.perform(
                post("/api/v1/payments/momo/ipn")
                        .param("partnerCode", partnerCode)
                        .param("orderId", txnRef)
                        .param("requestId", requestId)
                        .param("amount", String.valueOf(amount))
                        .param("orderInfo", orderInfo)
                        .param("orderType", orderType)
                        .param("transId", String.valueOf(transId))
                        .param("resultCode", String.valueOf(resultCode))
                        .param("message", message)
                        .param("payType", payType)
                        .param("responseTime", String.valueOf(responseTime))
                        .param("extraData", extraData)
                        .param("signature", signature)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(0))
                .andExpect(jsonPath("$.message").value("Success"));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(updatedOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void handleMomoIpn_invalidSignature_shouldReturn99() throws Exception {
        mockMvc.perform(
                post("/api/v1/payments/momo/ipn")
                        .param("orderId", "ORD-UNKNOWN")
                        .param("signature", "invalidsignature")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(99));
    }

    @Test
    void handleMomoReturn_shouldRedirectToStorefront() throws Exception {
        mockMvc.perform(
                get("/api/v1/payments/momo/return")
                        .param("orderId", "ORD-12345_PAY_9999")
                        .param("resultCode", "0")
        )
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost:3000/checkout/result?orderNumber=ORD-12345&status=SUCCESS"));
    }

    @Test
    void handleStripeWebhook_validPayload_shouldConfirmOrderAndPayment() throws Exception {
        User user = createUser();
        Order order = createOrder(user, PaymentMethod.STRIPE, new BigDecimal("500000.00"));

        Payment payment = new Payment(order, PaymentMethod.STRIPE, new BigDecimal("500000.00"));
        String txnRef = order.getOrderNumber() + "_PAY_" + System.currentTimeMillis();
        payment.setTransactionReference(txnRef);
        payment.setProvider("STRIPE");
        payment = paymentRepository.save(payment);

        String payload = """
                {
                  "id": "evt_test_123",
                  "object": "event",
                  "api_version": "2024-06-20",
                  "created": 1729000000,
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test_session_123",
                      "object": "checkout.session",
                      "amount_total": 500000,
                      "client_reference_id": "%s",
                      "currency": "vnd",
                      "payment_status": "paid"
                    }
                  }
                }
                """.formatted(txnRef);

        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;
        String signature = Webhook.Util.computeHmacSha256(stripeProperties.getWebhookSecret(), signedPayload);
        String signatureHeader = "t=" + timestamp + ",v1=" + signature;

        mockMvc.perform(
                post("/api/v1/payments/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signatureHeader)
                        .content(payload)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(updatedOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void handleStripeReturn_shouldRedirectToStorefront() throws Exception {
        mockMvc.perform(
                get("/api/v1/payments/stripe/return")
                        .param("orderNumber", "ORD-999")
                        .param("status", "SUCCESS")
        )
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost:3000/checkout/result?orderNumber=ORD-999&status=SUCCESS"));
    }

    private User createUser() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = "user_" + unique + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);
        User user = userService.register(email, passwordHash, "User " + unique, null);
        createdUserIds.add(user.getId());
        return user;
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
        order.setShippingRecipientName("John Doe");
        order.setShippingPhone("0912345678");
        order.setShippingAddress("123 Test Street, Hanoi");
        order.setShippingCity("Hanoi");
        return orderRepository.save(order);
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
