package com.aitechmarketplace.backend.cart.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        CartProductResponse product,
        Integer quantity,
        BigDecimal subtotal
) {
}
