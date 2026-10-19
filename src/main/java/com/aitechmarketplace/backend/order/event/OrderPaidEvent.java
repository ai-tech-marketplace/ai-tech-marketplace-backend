package com.aitechmarketplace.backend.order.event;

import com.aitechmarketplace.backend.order.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Retained for backwards compatibility with existing serialized Kafka events.
 */
public record OrderPaidEvent(
        Long orderId,
        String orderNumber,
        Long userId,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        String providerTransactionId,
        OffsetDateTime paidAt
) {}
