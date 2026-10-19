package com.aitechmarketplace.backend.event;

import com.aitechmarketplace.backend.order.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderPaidEvent(
        Long orderId,
        String orderNumber,
        Long userId,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        String providerTransactionId,
        OffsetDateTime paidAt
) {}
