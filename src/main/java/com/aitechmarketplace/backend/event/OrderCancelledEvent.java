package com.aitechmarketplace.backend.event;

import java.time.OffsetDateTime;

public record OrderCancelledEvent(
        String eventId,
        String orderNumber,
        Long userId,
        String reason,
        OffsetDateTime occurredAt
) {
}
