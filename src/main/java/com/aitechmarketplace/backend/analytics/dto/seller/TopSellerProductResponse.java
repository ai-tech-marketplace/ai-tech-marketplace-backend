package com.aitechmarketplace.backend.analytics.dto.seller;

import java.math.BigDecimal;

public record TopSellerProductResponse(
        Long productId,
        String productName,
        String sku,
        long unitsSold,
        BigDecimal grossRevenue,
        BigDecimal netRevenue
) {
}
