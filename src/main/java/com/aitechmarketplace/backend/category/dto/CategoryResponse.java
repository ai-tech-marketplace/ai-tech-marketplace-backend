package com.aitechmarketplace.backend.category.dto;

import com.aitechmarketplace.backend.category.entity.Category;

public record CategoryResponse(
    Long id,
    String name,
    String slug,
    String description
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getName(),
            category.getSlug(),
            category.getDescription()
        );
    }
}
