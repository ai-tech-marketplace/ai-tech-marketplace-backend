package com.aitechmarketplace.backend.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must not exceed 100 characters")
    String name,

    @Size(max = 150, message = "Slug must not exceed 150 characters")
    String slug,

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    String description,

    String status
) {
    public CategoryUpdateRequest(String name, String description) {
        this(name, null, description, null);
    }
}
