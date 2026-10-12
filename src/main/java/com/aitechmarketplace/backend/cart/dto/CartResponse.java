package com.aitechmarketplace.backend.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        Long id,
        List<CartItemResponse> items,
        Integer totalItems,
        BigDecimal totalAmount
) {
}
