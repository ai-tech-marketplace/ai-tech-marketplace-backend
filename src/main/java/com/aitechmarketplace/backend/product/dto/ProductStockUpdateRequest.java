package com.aitechmarketplace.backend.product.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductStockUpdateRequest(

        @NotNull(message = "Stock quantity is required")
        @PositiveOrZero(
                message = "Stock quantity must be greater than or equal to 0"
        )
        Integer stockQuantity

) {
}
