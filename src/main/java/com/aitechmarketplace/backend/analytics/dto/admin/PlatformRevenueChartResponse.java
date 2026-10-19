package com.aitechmarketplace.backend.analytics.dto.admin;

import java.util.List;

public record PlatformRevenueChartResponse(
        String interval,
        List<RevenueTimelinePoint> timeline
) {
}
