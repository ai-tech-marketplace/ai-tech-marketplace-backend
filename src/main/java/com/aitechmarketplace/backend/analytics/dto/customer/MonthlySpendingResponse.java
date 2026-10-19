package com.aitechmarketplace.backend.analytics.dto.customer;

import java.util.List;

public record MonthlySpendingResponse(
        int months,
        List<MonthlySpendingPoint> timeline
) {
}
