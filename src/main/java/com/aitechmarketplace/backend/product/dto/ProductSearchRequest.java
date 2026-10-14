package com.aitechmarketplace.backend.product.dto;

import java.math.BigDecimal;

public record ProductSearchRequest(
    String keyword,
    Long categoryId,
    Long brandId,
    BigDecimal minPrice,
    BigDecimal maxPrice
) {
    public ProductSearchRequest(String keyword, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice) {
        this(keyword, categoryId, null, minPrice, maxPrice);
    }
}