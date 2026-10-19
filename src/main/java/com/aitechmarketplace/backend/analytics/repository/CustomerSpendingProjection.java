package com.aitechmarketplace.backend.analytics.repository;

import java.math.BigDecimal;
import java.time.Instant;

public interface CustomerSpendingProjection {
    BigDecimal getTotalSpent();
    BigDecimal getTotalSaved();
    Long getTotalOrdersPlaced();
    Long getCompletedOrders();
    Instant getFirstOrderDate();
    Instant getLastOrderDate();
}
