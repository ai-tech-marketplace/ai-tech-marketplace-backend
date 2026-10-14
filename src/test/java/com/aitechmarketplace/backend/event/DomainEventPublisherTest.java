package com.aitechmarketplace.backend.event;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EmbeddedKafka(partitions = 1, topics = EventTopics.ORDERS)
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class DomainEventPublisherTest {

    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    void publishOrderCreated_sendsToKafka() {
        var event = new OrderCreatedEvent(
                UUID.randomUUID().toString(), "ORD-TEST-001", 1L,
                new BigDecimal("100000.00"), "COD", List.of(), OffsetDateTime.now()
        );
        publisher.publishOrderCreated(event);

        var consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.aitechmarketplace.backend.event");
        var consumer = new DefaultKafkaConsumerFactory<String, Object>(consumerProps).createConsumer();
        embeddedKafka.consumeFromEmbeddedTopics(consumer, EventTopics.ORDERS);

        var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThanOrEqualTo(1);
        records.forEach(record -> assertThat(record.key()).isEqualTo("1")); // key must be userId, not orderNumber
        consumer.close();
    }

    @Test
    void publishOrderCancelled_sendsToKafka() {
        var event = new OrderCancelledEvent(
                UUID.randomUUID().toString(), "ORD-TEST-002", 1L,
                "Customer requested cancellation", OffsetDateTime.now()
        );
        publisher.publishOrderCancelled(event);

        var consumerProps = KafkaTestUtils.consumerProps("test-group-2", "true", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.aitechmarketplace.backend.event");
        var consumer = new DefaultKafkaConsumerFactory<String, Object>(consumerProps).createConsumer();
        embeddedKafka.consumeFromEmbeddedTopics(consumer, EventTopics.ORDERS);

        var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThanOrEqualTo(1);
        records.forEach(record -> assertThat(record.key()).isEqualTo("1")); // key must be userId, not orderNumber
        consumer.close();
    }
}
