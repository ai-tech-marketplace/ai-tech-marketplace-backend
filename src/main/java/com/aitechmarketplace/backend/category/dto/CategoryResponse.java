package com.aitechmarketplace.backend.category.dto;

import com.aitechmarketplace.backend.category.entity.Category;

import java.time.OffsetDateTime;

public record CategoryResponse(
    Long id,
    String name,
    String description,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getName(),
            category.getDescription(),
            category.isActive(),
            category.getCreatedAt(),
            category.getUpdatedAt()
        );
    }
}
