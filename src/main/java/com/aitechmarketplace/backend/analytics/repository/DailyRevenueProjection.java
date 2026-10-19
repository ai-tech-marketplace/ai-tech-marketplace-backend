package com.aitechmarketplace.backend.analytics.repository;

import java.math.BigDecimal;

public interface DailyRevenueProjection {
    String getDateStr();
    BigDecimal getGmv();
    BigDecimal getCommission();
    Long getOrderCount();
}
