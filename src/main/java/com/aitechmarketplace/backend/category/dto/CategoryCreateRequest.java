package com.aitechmarketplace.backend.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(

    @NotBlank(message = "Category name is required")
    @Size(
        max = 255,
        message = "Category name must not exceed 255 characters"
    )
    String name,

    @Size(
        max = 5000,
        message = "Description must not exceed 5000 characters"
    )
    String description
) {}
