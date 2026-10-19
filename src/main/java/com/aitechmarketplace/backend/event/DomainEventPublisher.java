package com.aitechmarketplace.backend.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DomainEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing OrderCreatedEvent for order: {}, userId: {}", event.orderNumber(), event.userId());
        kafkaTemplate.send(EventTopics.ORDERS, event.userId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCreatedEvent for order: {}, userId: {}", event.orderNumber(), event.userId(), ex);
                    } else {
                        log.info("Successfully published OrderCreatedEvent for order: {}, userId: {}, partition: {}",
                                event.orderNumber(), event.userId(), result.getRecordMetadata().partition());
                    }
                });
    }

    public void publishOrderCancelled(OrderCancelledEvent event) {
        log.info("Publishing OrderCancelledEvent for order: {}, userId: {}", event.orderNumber(), event.userId());
        kafkaTemplate.send(EventTopics.ORDERS, event.userId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCancelledEvent for order: {}, userId: {}", event.orderNumber(), event.userId(), ex);
                    } else {
                        log.info("Successfully published OrderCancelledEvent for order: {}, userId: {}, partition: {}",
                                event.orderNumber(), event.userId(), result.getRecordMetadata().partition());
                    }
                });
    }

    public void publishOrderPaid(OrderPaidEvent event) {
        log.info("Publishing OrderPaidEvent for order: {}, userId: {}", event.orderNumber(), event.userId());
        kafkaTemplate.send(EventTopics.ORDERS, event.userId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderPaidEvent for order: {}, userId: {}", event.orderNumber(), event.userId(), ex);
                    } else {
                        log.info("Successfully published OrderPaidEvent for order: {}, userId: {}, partition: {}",
                                event.orderNumber(), event.userId(), result.getRecordMetadata().partition());
                    }
                });
    }
}
