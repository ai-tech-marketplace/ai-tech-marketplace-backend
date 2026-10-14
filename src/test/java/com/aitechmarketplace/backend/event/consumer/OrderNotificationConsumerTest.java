package com.aitechmarketplace.backend.event.consumer;

import com.aitechmarketplace.backend.event.EventTopics;
import com.aitechmarketplace.backend.event.OrderCreatedEvent;
import com.aitechmarketplace.backend.event.OrderItemEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@EmbeddedKafka(partitions = 1, topics = {EventTopics.ORDERS})
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class OrderNotificationConsumerTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoSpyBean
    private OrderNotificationConsumer consumer;

    @Test
    void consumesOrderCreatedEvent() throws Exception {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                "ORD-TEST-CONSUMER",
                1L,
                new BigDecimal("100000"),
                "COD",
                List.<OrderItemEvent>of(),
                OffsetDateTime.now()
        );
        kafkaTemplate.send(EventTopics.ORDERS, event.orderNumber(), event).get();

        // Spring Kafka passes ConsumerRecord when @Payload Object is used;
        // verify the raw argument contains an OrderCreatedEvent with the correct orderNumber.
        verify(consumer, timeout(10000)).handleOrderEvent(argThat(raw -> {
            if (raw instanceof ConsumerRecord<?, ?> cr
                    && cr.value() instanceof OrderCreatedEvent e) {
                return e.orderNumber().equals("ORD-TEST-CONSUMER");
            }
            if (raw instanceof OrderCreatedEvent e) {
                return e.orderNumber().equals("ORD-TEST-CONSUMER");
            }
            return false;
        }));
    }
}
