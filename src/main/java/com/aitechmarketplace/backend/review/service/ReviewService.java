package com.aitechmarketplace.backend.review.service;

import com.aitechmarketplace.backend.review.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

    ReviewResponse createReview(Long userId, Long productId, ReviewCreateRequest request);

    ReviewResponse updateReview(Long userId, Long reviewId, ReviewUpdateRequest request);

    Page<ReviewResponse> getProductReviews(Long productId, Integer rating, Boolean hasImages, String sort, Pageable pageable);

    ReviewSummaryResponse getReviewSummary(Long productId);

    HelpfulVoteResponse toggleHelpfulVote(Long userId, Long reviewId);

    SellerReplyResponse postSellerReply(Long userId, boolean isAdmin, Long reviewId, SellerReplyRequest request);

    AdminReviewModerationResponse moderateReview(Long adminId, Long reviewId, AdminReviewModerationRequest request);
}
