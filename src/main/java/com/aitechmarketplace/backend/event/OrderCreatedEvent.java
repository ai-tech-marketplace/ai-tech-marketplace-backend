package com.aitechmarketplace.backend.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderCreatedEvent(
        String eventId,
        String orderNumber,
        Long userId,
        BigDecimal totalAmount,
        String paymentMethod,
        List<OrderItemEvent> items,
        OffsetDateTime occurredAt
) {
}
