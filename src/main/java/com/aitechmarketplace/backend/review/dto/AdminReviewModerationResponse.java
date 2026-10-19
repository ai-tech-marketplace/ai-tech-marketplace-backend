package com.aitechmarketplace.backend.review.dto;

import com.aitechmarketplace.backend.review.entity.ReviewStatus;

import java.time.OffsetDateTime;

public record AdminReviewModerationResponse(
        Long reviewId,
        ReviewStatus status,
        String hiddenReason,
        OffsetDateTime updatedAt
) {}
