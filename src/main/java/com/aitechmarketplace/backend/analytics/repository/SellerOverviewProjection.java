package com.aitechmarketplace.backend.analytics.repository;

import java.math.BigDecimal;

public interface SellerOverviewProjection {
    BigDecimal getGrossSales();
    BigDecimal getCommissionPaid();
    BigDecimal getNetEarnings();
    Long getTotalOrders();
    Long getTotalUnitsSold();
}
