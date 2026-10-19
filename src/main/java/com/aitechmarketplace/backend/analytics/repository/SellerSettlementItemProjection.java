package com.aitechmarketplace.backend.analytics.repository;

import com.aitechmarketplace.backend.settlement.entity.SettlementStatus;

import java.math.BigDecimal;
import java.time.Instant;

public interface SellerSettlementItemProjection {
    Long getSettlementId();
    String getOrderNumber();
    String getProductName();
    Integer getQuantity();
    BigDecimal getItemSubtotal();
    BigDecimal getCommissionRate();
    BigDecimal getCommissionAmount();
    BigDecimal getPlatformDiscountSubsidized();
    BigDecimal getSellerNetAmount();
    SettlementStatus getStatus();
    Instant getSettledAt();
    Instant getCreatedAt();
}
