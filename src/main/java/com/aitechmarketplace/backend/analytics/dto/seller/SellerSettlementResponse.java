package com.aitechmarketplace.backend.analytics.dto.seller;

import com.aitechmarketplace.backend.settlement.entity.SettlementStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SellerSettlementResponse(
        Long settlementId,
        String orderNumber,
        String productName,
        int quantity,
        BigDecimal itemSubtotal,
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        BigDecimal platformDiscountSubsidized,
        BigDecimal sellerNetAmount,
        SettlementStatus status,
        OffsetDateTime settledAt,
        OffsetDateTime createdAt
) {
}
