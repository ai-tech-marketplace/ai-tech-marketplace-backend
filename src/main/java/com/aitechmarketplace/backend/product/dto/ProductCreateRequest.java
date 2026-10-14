package com.aitechmarketplace.backend.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductCreateRequest(
    @Size(max = 100, message = "SKU must not exceed 100 characters")
    String sku,

    @NotBlank(message = "Product name is required")
    @Size(
        max = 255,
        message = "Product name must not exceed 255 characters"
    )
    String name,

    @Size(max = 300, message = "Slug must not exceed 300 characters")
    String slug,

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

    @Min(
        value = 0,
        message = "Stock quantity must be greater than or equal to 0"
    )
    Integer stockQuantity,

    Long categoryId,

    List<Long> categoryIds,

    Long brandId,

    Long sellerId,

    Map<String, Object> specifications
) {
    public ProductCreateRequest(
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        List<Long> categoryIds
    ) {
        this(null, name, null, description, price, stockQuantity, null, categoryIds, null, null, null);
    }

    public Long resolveCategoryId() {
        if (categoryId != null) {
            return categoryId;
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            return categoryIds.get(0);
        }
        return null;
    }
}