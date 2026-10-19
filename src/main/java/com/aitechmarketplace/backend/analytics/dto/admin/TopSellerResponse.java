package com.aitechmarketplace.backend.analytics.dto.admin;

import java.math.BigDecimal;

public record TopSellerResponse(
        Long sellerId,
        String sellerName,
        long totalOrders,
        BigDecimal grossSales,
        BigDecimal commissionPaid,
        BigDecimal sellerNetAmount
) {
}
