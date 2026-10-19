package com.aitechmarketplace.backend.review.dto;

import com.aitechmarketplace.backend.review.entity.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminReviewModerationRequest(
        @NotNull(message = "status is required")
        ReviewStatus status,

        @Size(max = 255, message = "hiddenReason must not exceed 255 characters")
        String hiddenReason
) {}
