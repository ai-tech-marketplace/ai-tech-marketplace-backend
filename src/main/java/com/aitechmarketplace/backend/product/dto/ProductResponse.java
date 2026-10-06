package com.aitechmarketplace.backend.product.dto;

import com.aitechmarketplace.backend.product.entity.Product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ProductResponse(
        Long id,
        Long sellerId,
        String sellerEmail,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<Long> categoryIds) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getSeller().getEmail(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getCategories()
                        .stream()
                        .map(category -> category.getId())
                        .toList());
    }

}
