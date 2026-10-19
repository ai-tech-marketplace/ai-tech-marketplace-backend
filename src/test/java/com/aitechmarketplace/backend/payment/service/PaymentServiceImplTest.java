package com.aitechmarketplace.backend.payment.service;

import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.common.exception.ForbiddenException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.event.DomainEventPublisher;
import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import com.aitechmarketplace.backend.order.entity.Payment;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.order.entity.PaymentStatus;
import com.aitechmarketplace.backend.event.OrderPaidEvent;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.order.repository.PaymentRepository;
import com.aitechmarketplace.backend.payment.dto.PaymentInitiateRequest;
import com.aitechmarketplace.backend.payment.dto.PaymentInitiateResponse;
import com.aitechmarketplace.backend.payment.dto.PaymentVerificationResult;
import com.aitechmarketplace.backend.payment.provider.PaymentGatewayProvider;
import com.aitechmarketplace.backend.payment.provider.PaymentProviderFactory;
import com.aitechmarketplace.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentProviderFactory paymentProviderFactory;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private PaymentGatewayProvider paymentGatewayProvider;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User user;
    private Order order;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(10L);
        user.setEmail("buyer@example.com");

        order = new Order();
        order.setId(100L);
        order.setOrderNumber("ORD-100");
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setPaymentMethod(PaymentMethod.VNPAY);
        order.setTotalAmount(new BigDecimal("500000.00"));
    }

    @Test
    void initiatePayment_shouldSucceedForValidPendingOrder() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(paymentProviderFactory.getProvider(PaymentMethod.VNPAY)).thenReturn(paymentGatewayProvider);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentInitiateResponse expectedResponse = new PaymentInitiateResponse(
                "ORD-100_PAY_123",
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?foo=bar",
                OffsetDateTime.now().plusMinutes(15)
        );
        when(paymentGatewayProvider.initiatePayment(eq(order), any(Payment.class), eq("127.0.0.1"), eq("http://localhost:3000/return")))
                .thenReturn(expectedResponse);

        PaymentInitiateRequest request = new PaymentInitiateRequest(PaymentMethod.VNPAY, "http://localhost:3000/return");
        PaymentInitiateResponse result = paymentService.initiatePayment(10L, 100L, request, "127.0.0.1");

        assertThat(result).isNotNull();
        assertThat(result.paymentUrl()).isEqualTo(expectedResponse.paymentUrl());

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getTransactionReference()).startsWith("ORD-100_PAY_");
        assertThat(savedPayment.getProvider()).isEqualTo("VNPAY");
        assertThat(savedPayment.getAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));
    }

    @Test
    void initiatePayment_shouldThrowNotFoundWhenOrderDoesNotExist() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        PaymentInitiateRequest request = new PaymentInitiateRequest(PaymentMethod.VNPAY, null);
        assertThatThrownBy(() -> paymentService.initiatePayment(10L, 999L, request, "127.0.0.1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void initiatePayment_shouldThrowForbiddenWhenUserDoesNotOwnOrder() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        PaymentInitiateRequest request = new PaymentInitiateRequest(PaymentMethod.VNPAY, null);
        assertThatThrownBy(() -> paymentService.initiatePayment(99L, 100L, request, "127.0.0.1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void initiatePayment_shouldThrowBadRequestWhenOrderNotPending() {
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        PaymentInitiateRequest request = new PaymentInitiateRequest(PaymentMethod.VNPAY, null);
        assertThatThrownBy(() -> paymentService.initiatePayment(10L, 100L, request, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void initiatePayment_shouldThrowBadRequestWhenOrderAlreadyPaid() {
        order.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        PaymentInitiateRequest request = new PaymentInitiateRequest(PaymentMethod.VNPAY, null);
        assertThatThrownBy(() -> paymentService.initiatePayment(10L, 100L, request, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already paid");
    }

    @Test
    void handleVnpayIpn_shouldProcessSuccessfulPaymentAndPublishEvent() {
        when(paymentProviderFactory.getProvider(PaymentMethod.VNPAY)).thenReturn(paymentGatewayProvider);

        Map<String, String> params = Map.of("vnp_TxnRef", "ORD-100_PAY_1", "vnp_ResponseCode", "00");
        PaymentVerificationResult verifyResult = new PaymentVerificationResult(
                true,
                true,
                "ORD-100_PAY_1",
                "VNPAY-TRANS-999",
                new BigDecimal("500000.00"),
                "00",
                "Success",
                "raw"
        );
        when(paymentGatewayProvider.verifyCallback(params, null, null)).thenReturn(verifyResult);

        Payment payment = new Payment(order, PaymentMethod.VNPAY, new BigDecimal("500000.00"));
        payment.setTransactionReference("ORD-100_PAY_1");
        when(paymentRepository.findByTransactionReference("ORD-100_PAY_1")).thenReturn(Optional.of(payment));

        Map<String, Object> result = paymentService.handleVnpayIpn(params);

        assertThat(result.get("RspCode")).isEqualTo("00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        verify(paymentRepository).save(payment);
        verify(orderRepository).save(order);
        verify(domainEventPublisher).publishOrderPaid(any(OrderPaidEvent.class));
    }

    @Test
    void handleVnpayIpn_shouldReturn97OnInvalidSignature() {
        when(paymentProviderFactory.getProvider(PaymentMethod.VNPAY)).thenReturn(paymentGatewayProvider);

        Map<String, String> params = Map.of("vnp_TxnRef", "ORD-100_PAY_1");
        PaymentVerificationResult verifyResult = new PaymentVerificationResult(
                false, false, "ORD-100_PAY_1", null, null, "97", "Invalid Signature", "raw"
        );
        when(paymentGatewayProvider.verifyCallback(params, null, null)).thenReturn(verifyResult);

        Map<String, Object> result = paymentService.handleVnpayIpn(params);
        assertThat(result.get("RspCode")).isEqualTo("97");
        verify(paymentRepository, never()).save(any());
        verify(domainEventPublisher, never()).publishOrderPaid(any());
    }

    @Test
    void handleVnpayIpn_shouldReturn02WhenPaymentAlreadyPaid() {
        when(paymentProviderFactory.getProvider(PaymentMethod.VNPAY)).thenReturn(paymentGatewayProvider);

        Map<String, String> params = Map.of("vnp_TxnRef", "ORD-100_PAY_1");
        PaymentVerificationResult verifyResult = new PaymentVerificationResult(
                true, true, "ORD-100_PAY_1", "TXN123", new BigDecimal("500000.00"), "00", "Success", "raw"
        );
        when(paymentGatewayProvider.verifyCallback(params, null, null)).thenReturn(verifyResult);

        Payment payment = new Payment(order, PaymentMethod.VNPAY, new BigDecimal("500000.00"));
        payment.setStatus(PaymentStatus.PAID);
        payment.setTransactionReference("ORD-100_PAY_1");
        when(paymentRepository.findByTransactionReference("ORD-100_PAY_1")).thenReturn(Optional.of(payment));

        Map<String, Object> result = paymentService.handleVnpayIpn(params);
        assertThat(result.get("RspCode")).isEqualTo("02");
        verify(domainEventPublisher, never()).publishOrderPaid(any());
    }

    @Test
    void handleVnpayIpn_shouldReturn04AndMarkFailedWhenAmountMismatched() {
        when(paymentProviderFactory.getProvider(PaymentMethod.VNPAY)).thenReturn(paymentGatewayProvider);

        Map<String, String> params = Map.of("vnp_TxnRef", "ORD-100_PAY_1");
        PaymentVerificationResult verifyResult = new PaymentVerificationResult(
                true, true, "ORD-100_PAY_1", "TXN123", new BigDecimal("100000.00"), "00", "Success", "raw"
        );
        when(paymentGatewayProvider.verifyCallback(params, null, null)).thenReturn(verifyResult);

        Payment payment = new Payment(order, PaymentMethod.VNPAY, new BigDecimal("500000.00"));
        payment.setTransactionReference("ORD-100_PAY_1");
        when(paymentRepository.findByTransactionReference("ORD-100_PAY_1")).thenReturn(Optional.of(payment));

        Map<String, Object> result = paymentService.handleVnpayIpn(params);
        assertThat(result.get("RspCode")).isEqualTo("04");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
        verify(domainEventPublisher, never()).publishOrderPaid(any());
    }

    @Test
    void handleVnpayIpn_decisionA_latePaymentOnCancelledOrderSetsPaidAfterCancelled() {
        when(paymentProviderFactory.getProvider(PaymentMethod.VNPAY)).thenReturn(paymentGatewayProvider);

        order.setStatus(OrderStatus.CANCELLED);
        Map<String, String> params = Map.of("vnp_TxnRef", "ORD-100_PAY_1");
        PaymentVerificationResult verifyResult = new PaymentVerificationResult(
                true, true, "ORD-100_PAY_1", "TXN123", new BigDecimal("500000.00"), "00", "Success", "raw"
        );
        when(paymentGatewayProvider.verifyCallback(params, null, null)).thenReturn(verifyResult);

        Payment payment = new Payment(order, PaymentMethod.VNPAY, new BigDecimal("500000.00"));
        payment.setTransactionReference("ORD-100_PAY_1");
        when(paymentRepository.findByTransactionReference("ORD-100_PAY_1")).thenReturn(Optional.of(payment));

        Map<String, Object> result = paymentService.handleVnpayIpn(params);
        assertThat(result.get("RspCode")).isEqualTo("00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID_AFTER_CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED); // Remains CANCELLED
        verify(paymentRepository).save(payment);
        verify(orderRepository, never()).save(order);
        verify(domainEventPublisher, never()).publishOrderPaid(any());
    }

    @Test
    void handleMomoIpn_shouldProcessSuccess() {
        when(paymentProviderFactory.getProvider(PaymentMethod.MOMO)).thenReturn(paymentGatewayProvider);

        Map<String, String> params = Map.of("orderId", "ORD-100_PAY_1");
        PaymentVerificationResult verifyResult = new PaymentVerificationResult(
                true, true, "ORD-100_PAY_1", "MOMO-TXN-1", new BigDecimal("500000.00"), "0", "Success", "raw"
        );
        when(paymentGatewayProvider.verifyCallback(params, "rawPayload", null)).thenReturn(verifyResult);

        Payment payment = new Payment(order, PaymentMethod.MOMO, new BigDecimal("500000.00"));
        payment.setTransactionReference("ORD-100_PAY_1");
        when(paymentRepository.findByTransactionReference("ORD-100_PAY_1")).thenReturn(Optional.of(payment));

        Map<String, Object> result = paymentService.handleMomoIpn(params, "rawPayload");
        assertThat(result.get("resultCode")).isEqualTo(0);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void handleStripeWebhook_shouldProcessSuccess() {
        when(paymentProviderFactory.getProvider(PaymentMethod.STRIPE)).thenReturn(paymentGatewayProvider);

        PaymentVerificationResult verifyResult = new PaymentVerificationResult(
                true, true, "ORD-100_PAY_1", "cs_test_123", new BigDecimal("500000.00"), "paid", "Success", "raw"
        );
        when(paymentGatewayProvider.verifyCallback(null, "rawPayload", "sig")).thenReturn(verifyResult);

        Payment payment = new Payment(order, PaymentMethod.STRIPE, new BigDecimal("500000.00"));
        payment.setTransactionReference("ORD-100_PAY_1");
        when(paymentRepository.findByTransactionReference("ORD-100_PAY_1")).thenReturn(Optional.of(payment));

        Map<String, Object> result = paymentService.handleStripeWebhook("rawPayload", "sig");
        assertThat(result.get("received")).isEqualTo(true);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
