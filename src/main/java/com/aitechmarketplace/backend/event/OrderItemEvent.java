package com.aitechmarketplace.backend.event;

import java.math.BigDecimal;

public record OrderItemEvent(
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice
) {
}
