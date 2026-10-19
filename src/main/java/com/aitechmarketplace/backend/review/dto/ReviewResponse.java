package com.aitechmarketplace.backend.review.dto;

import com.aitechmarketplace.backend.review.entity.Review;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

public record ReviewResponse(
        Long id,
        Long productId,
        String authorName,
        Integer rating,
        String comment,
        Boolean isVerifiedPurchase,
        Boolean isEdited,
        Integer helpfulCount,
        List<ReviewImageResponse> images,
        String sellerReply,
        OffsetDateTime sellerRepliedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ReviewResponse from(Review review) {
        List<ReviewImageResponse> imageResponses = review.getImages() != null
                ? review.getImages().stream().map(ReviewImageResponse::from).toList()
                : Collections.emptyList();

        String author = review.getUser() != null ? review.getUser().getFullName() : "Anonymous";

        return new ReviewResponse(
                review.getId(),
                review.getProduct().getId(),
                author,
                review.getRating(),
                review.getComment(),
                review.getIsVerifiedPurchase(),
                review.getIsEdited(),
                review.getHelpfulCount(),
                imageResponses,
                review.getSellerReply(),
                review.getSellerRepliedAt(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
