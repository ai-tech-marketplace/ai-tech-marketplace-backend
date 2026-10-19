package com.aitechmarketplace.backend.analytics.repository;

import java.math.BigDecimal;

public interface TopSellerProjection {
    Long getSellerId();
    String getSellerName();
    Long getTotalOrders();
    BigDecimal getGrossSales();
    BigDecimal getCommissionPaid();
    BigDecimal getSellerNetAmount();
}
