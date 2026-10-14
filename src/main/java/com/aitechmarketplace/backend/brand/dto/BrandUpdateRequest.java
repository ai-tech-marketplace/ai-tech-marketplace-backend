package com.aitechmarketplace.backend.brand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrandUpdateRequest(
    @NotBlank(message = "Brand name is required")
    @Size(max = 100, message = "Brand name must not exceed 100 characters")
    String name,

    @Size(max = 150, message = "Slug must not exceed 150 characters")
    String slug,

    String status
) {
    public BrandUpdateRequest(String name, String slug) {
        this(name, slug, null);
    }
}
