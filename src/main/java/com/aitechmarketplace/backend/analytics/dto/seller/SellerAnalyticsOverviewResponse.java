package com.aitechmarketplace.backend.analytics.dto.seller;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SellerAnalyticsOverviewResponse(
        String currency,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        BigDecimal grossSales,
        BigDecimal commissionPaid,
        BigDecimal netEarnings,
        BigDecimal pendingSettlement,
        BigDecimal availableBalance,
        long totalOrders,
        long totalUnitsSold
) {
}
