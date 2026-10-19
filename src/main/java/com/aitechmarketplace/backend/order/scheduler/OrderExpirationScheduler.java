package com.aitechmarketplace.backend.order.scheduler;

import com.aitechmarketplace.backend.coupon.service.CouponService;
import com.aitechmarketplace.backend.event.OrderEventBridge;
import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.OrderItem;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import com.aitechmarketplace.backend.order.entity.Payment;
import com.aitechmarketplace.backend.order.entity.PaymentStatus;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.order.repository.PaymentRepository;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.settlement.entity.SellerOrderSettlement;
import com.aitechmarketplace.backend.settlement.entity.SettlementStatus;
import com.aitechmarketplace.backend.settlement.repository.SellerOrderSettlementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class OrderExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationScheduler.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final CouponService couponService;
    private final SellerOrderSettlementRepository sellerOrderSettlementRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public OrderExpirationScheduler(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            PaymentRepository paymentRepository,
            CouponService couponService,
            SellerOrderSettlementRepository sellerOrderSettlementRepository,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
        this.couponService = couponService;
        this.sellerOrderSettlementRepository = sellerOrderSettlementRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cancelExpiredOrders() {
        OffsetDateTime cutoffTime = OffsetDateTime.now().minusMinutes(15);
        List<Order> expiredOrders = orderRepository.findExpiredPendingOrders(cutoffTime);

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("Found {} expired pending order(s) to auto-cancel", expiredOrders.size());

        for (Order order : expiredOrders) {
            try {
                cancelSingleOrder(order);
            } catch (Exception ex) {
                log.error("Failed to auto-cancel expired order {}: {}", order.getOrderNumber(), ex.getMessage(), ex);
            }
        }
    }

    private void cancelSingleOrder(Order order) {
        if (order == null || order.getStatus() != OrderStatus.PENDING || order.getPaymentStatus() == PaymentStatus.PAID) {
            return;
        }

        // Restock inventory for items
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null) {
                    Product product = productRepository.findByIdForUpdate(item.getProduct().getId()).orElse(null);
                    if (product != null) {
                        product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                        productRepository.save(product);
                    }
                }
            }
        }

        // Rollback coupon usage (status -> RELEASED)
        couponService.rollbackCouponUsage(order.getId());

        // Cancel seller settlements if any exist
        List<SellerOrderSettlement> settlements = sellerOrderSettlementRepository.findByOrderId(order.getId());
        if (!settlements.isEmpty()) {
            for (SellerOrderSettlement settlement : settlements) {
                settlement.setStatus(SettlementStatus.CANCELLED);
            }
            sellerOrderSettlementRepository.saveAll(settlements);
        }

        // Mark associated pending payments as CANCELLED
        paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId()).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
            }
        });

        // Cancel order
        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);

        // Publish OrderCancelledApplicationEvent -> Kafka OrderCancelledEvent
        applicationEventPublisher.publishEvent(
                new OrderEventBridge.OrderCancelledApplicationEvent(savedOrder, "Payment timeout (15 minutes expired)")
        );

        log.info("Successfully auto-cancelled expired order {} and released inventory/coupons", order.getOrderNumber());
    }
}
