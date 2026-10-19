package com.aitechmarketplace.backend.analytics.repository;

import java.math.BigDecimal;

public interface CustomerMonthlySpendingProjection {
    String getMonthStr();
    BigDecimal getAmountSpent();
    Long getOrderCount();
}
