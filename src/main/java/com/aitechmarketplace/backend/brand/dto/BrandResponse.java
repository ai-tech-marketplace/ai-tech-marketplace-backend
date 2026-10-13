package com.aitechmarketplace.backend.brand.dto;

import com.aitechmarketplace.backend.brand.entity.Brand;

public record BrandResponse(
    Long id,
    String name,
    String slug,
    String status
) {
    public static BrandResponse from(Brand brand) {
        return new BrandResponse(
            brand.getId(),
            brand.getName(),
            brand.getSlug(),
            brand.getStatus()
        );
    }
}
