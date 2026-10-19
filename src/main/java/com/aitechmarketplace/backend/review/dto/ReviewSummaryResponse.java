package com.aitechmarketplace.backend.review.dto;

import java.util.Map;

public record ReviewSummaryResponse(
        Long productId,
        Double averageRating,
        Long totalReviews,
        Long reviewsWithImagesCount,
        Map<Integer, Long> ratingDistribution
) {}
