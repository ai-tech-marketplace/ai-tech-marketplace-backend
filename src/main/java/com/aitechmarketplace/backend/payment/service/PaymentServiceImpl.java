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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentProviderFactory paymentProviderFactory;
    private final DomainEventPublisher domainEventPublisher;

    public PaymentServiceImpl(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentProviderFactory paymentProviderFactory,
            DomainEventPublisher domainEventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentProviderFactory = paymentProviderFactory;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    @Transactional
    public PaymentInitiateResponse initiatePayment(Long userId, Long orderId, PaymentInitiateRequest request, String clientIp) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));

        if (!order.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not own this order");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Order is not in PENDING status: " + order.getStatus());
        }

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BadRequestException("Order is already paid");
        }

        PaymentGatewayProvider provider = paymentProviderFactory.getProvider(request.paymentMethod());

        // Generate unique merchant transaction reference for each attempt
        String txnRef = order.getOrderNumber() + "_PAY_" + System.currentTimeMillis();

        Payment payment = new Payment(order, request.paymentMethod(), order.getTotalAmount());
        payment.setTransactionReference(txnRef);
        payment.setProvider(request.paymentMethod().name());
        payment.setExpiresAt(OffsetDateTime.now().plusMinutes(15));
        payment = paymentRepository.save(payment);

        order.setPaymentMethod(request.paymentMethod());
        orderRepository.save(order);

        return provider.initiatePayment(order, payment, clientIp, request.returnUrl());
    }

    @Override
    @Transactional
    public Map<String, Object> handleVnpayIpn(Map<String, String> queryParams) {
        Map<String, Object> response = new HashMap<>();
        PaymentGatewayProvider provider = paymentProviderFactory.getProvider(PaymentMethod.VNPAY);
        PaymentVerificationResult result = provider.verifyCallback(queryParams, null, null);

        if (!result.isValidSignature()) {
            response.put("RspCode", "97");
            response.put("Message", "Invalid Checksum");
            return response;
        }

        Payment payment = paymentRepository.findByTransactionReference(result.transactionReference()).orElse(null);
        if (payment == null) {
            response.put("RspCode", "01");
            response.put("Message", "Order not found");
            return response;
        }

        Order order = payment.getOrder();

        if (payment.getStatus() == PaymentStatus.PAID) {
            response.put("RspCode", "02");
            response.put("Message", "Order already confirmed");
            return response;
        }

        if (result.amount() != null && result.amount().compareTo(order.getTotalAmount()) != 0) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setProviderResponse(result.rawResponse());
            paymentRepository.save(payment);
            response.put("RspCode", "04");
            response.put("Message", "Invalid Amount");
            return response;
        }

        processPaymentResult(payment, order, result);

        response.put("RspCode", "00");
        response.put("Message", "Confirm Success");
        return response;
    }

    @Override
    @Transactional
    public Map<String, Object> handleMomoIpn(Map<String, String> queryParams, String rawPayload) {
        Map<String, Object> response = new HashMap<>();
        PaymentGatewayProvider provider = paymentProviderFactory.getProvider(PaymentMethod.MOMO);
        PaymentVerificationResult result = provider.verifyCallback(queryParams, rawPayload, null);

        if (!result.isValidSignature()) {
            response.put("resultCode", 99);
            response.put("message", "Invalid signature");
            return response;
        }

        Payment payment = paymentRepository.findByTransactionReference(result.transactionReference()).orElse(null);
        if (payment == null) {
            response.put("resultCode", 1);
            response.put("message", "Order not found");
            return response;
        }

        Order order = payment.getOrder();

        if (payment.getStatus() == PaymentStatus.PAID) {
            response.put("resultCode", 0);
            response.put("message", "Already confirmed");
            return response;
        }

        if (result.amount() != null && result.amount().compareTo(order.getTotalAmount()) != 0) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setProviderResponse(result.rawResponse());
            paymentRepository.save(payment);
            response.put("resultCode", 2);
            response.put("message", "Invalid Amount");
            return response;
        }

        processPaymentResult(payment, order, result);

        response.put("resultCode", 0);
        response.put("message", "Success");
        return response;
    }

    @Override
    @Transactional
    public Map<String, Object> handleStripeWebhook(String rawPayload, String signatureHeader) {
        Map<String, Object> response = new HashMap<>();
        PaymentGatewayProvider provider = paymentProviderFactory.getProvider(PaymentMethod.STRIPE);
        PaymentVerificationResult result = provider.verifyCallback(null, rawPayload, signatureHeader);

        if (!result.isValidSignature()) {
            response.put("error", "Invalid signature");
            return response;
        }

        Payment payment = paymentRepository.findByTransactionReference(result.transactionReference()).orElse(null);
        if (payment == null) {
            response.put("received", true);
            return response;
        }

        Order order = payment.getOrder();

        if (payment.getStatus() == PaymentStatus.PAID) {
            response.put("received", true);
            return response;
        }

        if (result.amount() != null && result.amount().compareTo(order.getTotalAmount()) != 0) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setProviderResponse(result.rawResponse());
            paymentRepository.save(payment);
            response.put("received", true);
            return response;
        }

        processPaymentResult(payment, order, result);

        response.put("received", true);
        return response;
    }

    private void processPaymentResult(Payment payment, Order order, PaymentVerificationResult result) {
        if (!result.isSuccess()) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setProviderTransactionId(result.providerTransactionId());
            payment.setProviderResponse(result.rawResponse());
            paymentRepository.save(payment);
            return;
        }

        // Decision A: Late Payment arriving after order cancellation
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("ALERT_CUSTOMER_REFUND_NEEDED: Order {} received late payment after cancellation. Provider Txn: {}",
                    order.getOrderNumber(), result.providerTransactionId());
            payment.setStatus(PaymentStatus.PAID_AFTER_CANCELLED);
            payment.setPaidAt(OffsetDateTime.now());
            payment.setProviderTransactionId(result.providerTransactionId());
            payment.setProviderResponse(result.rawResponse());
            paymentRepository.save(payment);
            return;
        }

        // Standard successful payment transition
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(OffsetDateTime.now());
        payment.setProviderTransactionId(result.providerTransactionId());
        payment.setProviderResponse(result.rawResponse());
        paymentRepository.save(payment);

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        // Publish OrderPaidEvent to Kafka
        OrderPaidEvent paidEvent = new OrderPaidEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getUser().getId(),
                order.getTotalAmount(),
                payment.getPaymentMethod(),
                payment.getProviderTransactionId(),
                payment.getPaidAt()
        );
        domainEventPublisher.publishOrderPaid(paidEvent);
    }
}
