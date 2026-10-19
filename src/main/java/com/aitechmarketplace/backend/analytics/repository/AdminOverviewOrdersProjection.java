package com.aitechmarketplace.backend.analytics.repository;

import java.math.BigDecimal;

public interface AdminOverviewOrdersProjection {
    Long getTotalOrders();
    Long getCompletedOrders();
    Long getCancelledOrders();
    BigDecimal getGmv();
    BigDecimal getTotalDiscounts();
}
