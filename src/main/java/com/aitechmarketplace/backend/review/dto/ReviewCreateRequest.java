package com.aitechmarketplace.backend.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReviewCreateRequest(
        @NotNull(message = "orderItemId is required")
        Long orderItemId,

        @NotNull(message = "rating is required")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must be at most 5")
        Integer rating,

        @Size(max = 2000, message = "Comment must not exceed 2000 characters")
        String comment,

        @Size(max = 5, message = "Maximum 5 image URLs allowed")
        List<String> imageUrls
) {}
