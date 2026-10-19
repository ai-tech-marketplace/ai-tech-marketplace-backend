package com.aitechmarketplace.backend.review.dto;

import com.aitechmarketplace.backend.review.entity.ReviewImage;

public record ReviewImageResponse(
        Long id,
        String imageUrl,
        Integer displayOrder
) {
    public static ReviewImageResponse from(ReviewImage image) {
        return new ReviewImageResponse(
                image.getId(),
                image.getImageUrl(),
                image.getDisplayOrder()
        );
    }
}
