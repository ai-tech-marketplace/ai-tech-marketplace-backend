package com.aitechmarketplace.backend.analytics.repository;

import java.math.BigDecimal;

public interface TopSellerProductProjection {
    Long getProductId();
    String getProductName();
    String getSku();
    Long getUnitsSold();
    BigDecimal getGrossRevenue();
    BigDecimal getNetRevenue();
}
