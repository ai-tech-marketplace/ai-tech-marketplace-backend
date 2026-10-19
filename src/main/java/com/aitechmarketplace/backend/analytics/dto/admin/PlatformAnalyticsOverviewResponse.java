package com.aitechmarketplace.backend.analytics.dto.admin;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PlatformAnalyticsOverviewResponse(
        String currency,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        BigDecimal gmv,
        BigDecimal netPlatformRevenue,
        long totalOrders,
        long completedOrders,
        long cancelledOrders,
        BigDecimal cancellationRate,
        BigDecimal totalDiscountsSubsidized,
        BigDecimal averageOrderValue
) {
}
