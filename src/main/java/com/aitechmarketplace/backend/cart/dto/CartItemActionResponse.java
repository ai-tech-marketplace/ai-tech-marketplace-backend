package com.aitechmarketplace.backend.cart.dto;

public record CartItemActionResponse(
        Long id,
        Long productId,
        Integer quantity
) {
}
