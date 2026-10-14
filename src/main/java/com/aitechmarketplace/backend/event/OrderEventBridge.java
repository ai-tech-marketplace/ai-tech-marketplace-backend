package com.aitechmarketplace.backend.event;

import com.aitechmarketplace.backend.order.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class OrderEventBridge {

    private static final Logger log = LoggerFactory.getLogger(OrderEventBridge.class);

    private final DomainEventPublisher domainEventPublisher;

    public OrderEventBridge(DomainEventPublisher domainEventPublisher) {
        this.domainEventPublisher = domainEventPublisher;
    }

    public record OrderCreatedApplicationEvent(Order order) {
    }

    public record OrderCancelledApplicationEvent(Order order, String reason) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderCreated(OrderCreatedApplicationEvent event) {
        Order order = event.order();
        log.info("Handling OrderCreatedApplicationEvent for order: {}", order.getOrderNumber());

        List<OrderItemEvent> items = order.getItems() != null
                ? order.getItems().stream()
                .map(item -> new OrderItemEvent(
                        item.getProduct() != null ? item.getProduct().getId() : null,
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice()))
                .toList()
                : List.of();

        OrderCreatedEvent domainEvent = new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                order.getOrderNumber(),
                order.getUser().getId(),
                order.getTotalAmount(),
                order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null,
                items,
                order.getCreatedAt() != null ? order.getCreatedAt() : OffsetDateTime.now()
        );

        domainEventPublisher.publishOrderCreated(domainEvent);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderCancelled(OrderCancelledApplicationEvent event) {
        Order order = event.order();
        log.info("Handling OrderCancelledApplicationEvent for order: {}", order.getOrderNumber());

        OrderCancelledEvent domainEvent = new OrderCancelledEvent(
                UUID.randomUUID().toString(),
                order.getOrderNumber(),
                order.getUser().getId(),
                event.reason(),
                OffsetDateTime.now()
        );

        domainEventPublisher.publishOrderCancelled(domainEvent);
    }
}
