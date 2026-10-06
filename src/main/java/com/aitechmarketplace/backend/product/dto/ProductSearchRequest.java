package com.aitechmarketplace.backend.product.dto;

import java.math.BigDecimal;

public record ProductSearchRequest(
String keyword,
Long categoryId,
BigDecimal minPrice,
BigDecimal maxPrice
) {
}