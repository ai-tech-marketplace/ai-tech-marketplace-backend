package com.aitechmarketplace.backend.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void orderCreatedEvent_serializesAndDeserializes() throws Exception {
        var event = new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                "ORD-20261014-ABCD1234",
                1L,
                new BigDecimal("500000.00"),
                "COD",
                List.of(new OrderItemEvent(10L, "Product A", 2, new BigDecimal("250000.00"))),
                OffsetDateTime.now()
        );
        String json = mapper.writeValueAsString(event);
        OrderCreatedEvent deserialized = mapper.readValue(json, OrderCreatedEvent.class);
        assertThat(deserialized.orderNumber()).isEqualTo("ORD-20261014-ABCD1234");
        assertThat(deserialized.items()).hasSize(1);
        assertThat(deserialized.items().get(0).productName()).isEqualTo("Product A");
    }

    @Test
    void orderCancelledEvent_serializesAndDeserializes() throws Exception {
        var event = new OrderCancelledEvent(
                UUID.randomUUID().toString(),
                "ORD-20261014-ABCD1234",
                1L,
                "User requested cancellation",
                OffsetDateTime.now()
        );
        String json = mapper.writeValueAsString(event);
        OrderCancelledEvent deserialized = mapper.readValue(json, OrderCancelledEvent.class);
        assertThat(deserialized.reason()).isEqualTo("User requested cancellation");
    }

    @Test
    void eventTopics_ordersTopicIsDefined() {
        assertThat(EventTopics.ORDERS).isEqualTo("marketplace.orders");
    }
}
