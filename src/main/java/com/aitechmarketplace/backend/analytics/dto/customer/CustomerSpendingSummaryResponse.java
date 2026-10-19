package com.aitechmarketplace.backend.analytics.dto.customer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CustomerSpendingSummaryResponse(
        String currency,
        BigDecimal totalSpent,
        BigDecimal totalSaved,
        long totalOrdersPlaced,
        long completedOrders,
        OffsetDateTime firstOrderDate,
        OffsetDateTime lastOrderDate
) {
}
