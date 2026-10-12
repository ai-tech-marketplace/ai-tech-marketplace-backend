package com.aitechmarketplace.backend.cart.dto;

import java.math.BigDecimal;

public record CartProductResponse(
        Long id,
        String name,
        BigDecimal price,
        String thumbnailUrl
) {
}
