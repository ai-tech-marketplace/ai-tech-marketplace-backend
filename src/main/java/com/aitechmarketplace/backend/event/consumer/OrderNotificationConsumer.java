package com.aitechmarketplace.backend.event.consumer;

import com.aitechmarketplace.backend.event.EventTopics;
import com.aitechmarketplace.backend.event.OrderCancelledEvent;
import com.aitechmarketplace.backend.event.OrderCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class OrderNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationConsumer.class);

    @KafkaListener(topics = EventTopics.ORDERS, groupId = "notification")
    public void handleOrderEvent(@Payload Object raw) {
        // Spring Kafka may pass the full ConsumerRecord when parameter type is Object
        Object event = raw instanceof ConsumerRecord<?, ?> cr ? cr.value() : raw;
        if (event instanceof OrderCreatedEvent created) {
            log.info("[NOTIFICATION] Order created: orderNumber={}, userId={}, total={}",
                    created.orderNumber(), created.userId(), created.totalAmount());
            // TODO: send order confirmation email
        } else if (event instanceof OrderCancelledEvent cancelled) {
            log.info("[NOTIFICATION] Order cancelled: orderNumber={}, userId={}, reason={}",
                    cancelled.orderNumber(), cancelled.userId(), cancelled.reason());
            // TODO: send order cancellation email
        } else {
            log.warn("[NOTIFICATION] Received unknown event type: {}",
                    event != null ? event.getClass().getName() : "null");
        }
    }
}
