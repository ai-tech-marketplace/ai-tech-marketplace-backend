package com.aitechmarketplace.backend.event.consumer;

import com.aitechmarketplace.backend.event.EventTopics;
import com.aitechmarketplace.backend.event.OrderCancelledEvent;
import com.aitechmarketplace.backend.event.OrderCreatedEvent;
import com.aitechmarketplace.backend.event.OrderPaidEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class OrderAnalyticsConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderAnalyticsConsumer.class);

    @KafkaListener(topics = EventTopics.ORDERS, groupId = "analytics")
    public void handleOrderEvent(@Payload Object raw) {
        // Spring Kafka may pass the full ConsumerRecord when parameter type is Object
        Object event = raw instanceof ConsumerRecord<?, ?> cr ? cr.value() : raw;
        if (event instanceof OrderCreatedEvent created) {
            log.info("[ANALYTICS] Order created: orderNumber={}, total={}, itemCount={}",
                    created.orderNumber(), created.totalAmount(),
                    created.items() != null ? created.items().size() : 0);
            // TODO: push to analytics pipeline (e.g. BigQuery, Segment)
        } else if (event instanceof OrderPaidEvent paid) {
            log.info("[ANALYTICS] Order paid: orderNumber={}, total={}, method={}",
                    paid.orderNumber(), paid.totalAmount(), paid.paymentMethod());
        } else if (event instanceof OrderCancelledEvent cancelled) {
            log.info("[ANALYTICS] Order cancelled: orderNumber={}, reason={}",
                    cancelled.orderNumber(), cancelled.reason());
            // TODO: track cancellation metrics
        } else {
            log.warn("[ANALYTICS] Received unknown event type: {}",
                    event != null ? event.getClass().getName() : "null");
        }
    }
}
