package com.aitechmarketplace.backend.cart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CartMergeRequest(
        @NotNull(message = "Items list cannot be null")
        List<@Valid CartItemAddRequest> items
) {
}
