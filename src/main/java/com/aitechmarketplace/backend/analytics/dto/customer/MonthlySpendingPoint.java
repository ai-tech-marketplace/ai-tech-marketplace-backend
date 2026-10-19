package com.aitechmarketplace.backend.analytics.dto.customer;

import java.math.BigDecimal;

public record MonthlySpendingPoint(
        String month,
        BigDecimal amountSpent,
        long orderCount
) {
}
