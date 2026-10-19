package com.aitechmarketplace.backend.analytics.repository;

import java.math.BigDecimal;

public interface CategoryGmvProjection {
    Long getCategoryId();
    String getCategoryName();
    Long getTotalUnitsSold();
    BigDecimal getGmv();
    BigDecimal getPlatformCommissionEarned();
}
