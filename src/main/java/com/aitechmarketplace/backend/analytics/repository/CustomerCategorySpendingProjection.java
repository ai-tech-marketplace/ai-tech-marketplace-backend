package com.aitechmarketplace.backend.analytics.repository;

import java.math.BigDecimal;

public interface CustomerCategorySpendingProjection {
    Long getCategoryId();
    String getCategoryName();
    BigDecimal getAmountSpent();
    Long getItemsPurchased();
}
