package com.aitechmarketplace.backend.product.dto;

import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.image.entity.ProductImage;

import java.math.BigDecimal;

public record ProductSummaryResponse(
    Long id,
    String sku,
    String name,
    String slug,
    BigDecimal price,
    NamedRef brand,
    NamedRef category,
    String thumbnailUrl,
    String status
) {
    public record NamedRef(Long id, String name) {}

    public static ProductSummaryResponse from(Product product) {
        NamedRef brandRef = product.getBrand() != null
                ? new NamedRef(product.getBrand().getId(), product.getBrand().getName())
                : null;
        NamedRef categoryRef = product.getCategory() != null
                ? new NamedRef(product.getCategory().getId(), product.getCategory().getName())
                : null;

        String thumbnailUrl = null;
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            ProductImage primaryOrFirst = product.getImages().stream()
                    .filter(ProductImage::isPrimary)
                    .findFirst()
                    .orElse(product.getImages().get(0));
            thumbnailUrl = "/api/v1/products/" + product.getId() + "/images/" + primaryOrFirst.getId() + "/content";
        }

        return new ProductSummaryResponse(
            product.getId(),
            product.getSku(),
            product.getName(),
            product.getSlug(),
            product.getPrice(),
            brandRef,
            categoryRef,
            thumbnailUrl,
            product.getStatus()
        );
    }
}
