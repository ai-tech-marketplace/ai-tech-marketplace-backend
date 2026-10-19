package com.aitechmarketplace.backend.analytics.dto.admin;

import java.math.BigDecimal;

public record CategoryGmvResponse(
        Long categoryId,
        String categoryName,
        long totalUnitsSold,
        BigDecimal gmv,
        BigDecimal percentageShare,
        BigDecimal platformCommissionEarned
) {
}
