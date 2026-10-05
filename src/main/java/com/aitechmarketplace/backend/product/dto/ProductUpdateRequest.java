package com.aitechmarketplace.backend.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateRequest(

    @NotBlank(message = "Product name is required")
    @Size(
        max = 255,
        message = "Product name must not exceed 255 characters"
    )
    String name,

    @Size(
        max = 5000,
        message = "Description must not exceed 5000 characters"
    )
    String description,

    @NotNull(message = "Price is required")
    @DecimalMin(
        value = "0.00",
        message = "Price must be greater than or equal to 0"
    )
    BigDecimal price,

    @NotNull(message = "Stock quantity is required")
    @Min(
        value = 0,
        message = "Stock quantity must be greater than or equal to 0"
    )
    Integer stockQuantity
) {
}