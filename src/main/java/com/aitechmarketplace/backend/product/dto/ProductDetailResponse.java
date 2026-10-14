package com.aitechmarketplace.backend.product.dto;

import com.aitechmarketplace.backend.product.entity.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductDetailResponse(
    Long id,
    String sku,
    String name,
    String slug,
    String description,
    BigDecimal price,
    NamedRef brand,
    NamedRef category,
    Map<String, Object> specifications,
    Inventory inventory,
    List<String> images,
    String status
) {
    public record NamedRef(Long id, String name) {}
    public record Inventory(Integer available) {}

    public static ProductDetailResponse from(Product product) {
        NamedRef brandRef = product.getBrand() != null
                ? new NamedRef(product.getBrand().getId(), product.getBrand().getName())
                : null;
        NamedRef categoryRef = product.getCategory() != null
                ? new NamedRef(product.getCategory().getId(), product.getCategory().getName())
                : null;

        List<String> imageUrls = product.getImages() != null
                ? product.getImages().stream()
                    .map(img -> "/api/v1/products/" + product.getId() + "/images/" + img.getId() + "/content")
                    .toList()
                : List.of();

        return new ProductDetailResponse(
            product.getId(),
            product.getSku(),
            product.getName(),
            product.getSlug(),
            product.getDescription(),
            product.getPrice(),
            brandRef,
            categoryRef,
            product.getSpecifications(),
            new Inventory(product.getStockQuantity()),
            imageUrls,
            product.getStatus()
        );
    }
}
