package com.aitechmarketplace.backend.analytics.dto.admin;

import java.math.BigDecimal;

public record RevenueTimelinePoint(
        String date,
        BigDecimal gmv,
        BigDecimal commission,
        long orderCount
) {
}
