package com.aitechmarketplace.backend.analytics.dto.customer;

import java.math.BigDecimal;

public record CategorySpendingResponse(
        Long categoryId,
        String categoryName,
        BigDecimal amountSpent,
        BigDecimal percentageShare,
        long itemsPurchased
) {
}
