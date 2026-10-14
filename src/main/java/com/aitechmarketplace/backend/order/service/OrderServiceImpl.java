package com.aitechmarketplace.backend.order.service;

import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.ForbiddenException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.order.dto.OrderCreateRequest;
import com.aitechmarketplace.backend.order.dto.OrderPageResponse;
import com.aitechmarketplace.backend.order.dto.OrderResponse;
import com.aitechmarketplace.backend.order.entity.*;
import com.aitechmarketplace.backend.order.repository.OrderItemRepository;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.order.repository.PaymentRepository;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.coupon.service.CouponService;
import com.aitechmarketplace.backend.event.OrderEventBridge;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final CouponService couponService;
    private final RedisLockRegistry checkoutLockRegistry;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final CheckoutTransactionService checkoutTransactionService;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            ProductRepository productRepository,
            CouponService couponService,
            @Qualifier("checkoutLockRegistry") RedisLockRegistry checkoutLockRegistry,
            ApplicationEventPublisher applicationEventPublisher,
            CheckoutTransactionService checkoutTransactionService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
        this.couponService = couponService;
        this.checkoutLockRegistry = checkoutLockRegistry;
        this.applicationEventPublisher = applicationEventPublisher;
        this.checkoutTransactionService = checkoutTransactionService;
    }

    /**
     * Acquires a per-user Redis distributed lock, then delegates to
     * {@link CheckoutTransactionService#execute} which runs inside its own
     * {@code @Transactional} boundary.
     *
     * <p>The lock is released in {@code finally} AFTER the inner method returns,
     * which means AFTER the Spring TransactionInterceptor has committed the
     * transaction — eliminating the race window that existed when
     * {@code @Transactional} and the lock acquisition lived in the same method.
     */
    @Override
    public OrderResponse createOrder(Long userId, OrderCreateRequest request) {
        Lock lock = checkoutLockRegistry.obtain("user:" + userId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConflictException("Checkout interrupted", "CHECKOUT_INTERRUPTED");
        }
        if (!acquired) {
            throw new ConflictException("Checkout already in progress", "CHECKOUT_IN_PROGRESS");
        }

        try {
            return checkoutTransactionService.execute(userId, request);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPageResponse getUserOrders(Long userId, OrderStatus status, Pageable pageable) {
        if (status != null) {
            return OrderPageResponse.from(orderRepository.findByUserIdAndStatus(userId, status, pageable).map(OrderResponse::from));
        }
        return OrderPageResponse.from(orderRepository.findByUserId(userId, pageable).map(OrderResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, Long userId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found", "ORDER_NOT_FOUND"));

        if (!isAdmin && !order.getUser().getId().equals(userId)) {
            throw new NotFoundException("Order not found", "ORDER_NOT_FOUND");
        }

        return OrderResponse.from(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId, Long userId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found", "ORDER_NOT_FOUND"));

        if (!isAdmin && !order.getUser().getId().equals(userId)) {
            throw new NotFoundException("Order not found", "ORDER_NOT_FOUND");
        }

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException("Order cannot be cancelled in status " + order.getStatus(), "ORDER_CANNOT_BE_CANCELLED");
        }

        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null) {
                Product product = productRepository.findByIdForUpdate(item.getProduct().getId()).orElse(null);
                if (product != null) {
                    product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                    productRepository.save(product);
                }
            }
        }

        couponService.rollbackCouponUsage(order.getId());

        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);

        applicationEventPublisher.publishEvent(new OrderEventBridge.OrderCancelledApplicationEvent(savedOrder, "User requested cancellation"));

        return OrderResponse.from(savedOrder);
    }
}
