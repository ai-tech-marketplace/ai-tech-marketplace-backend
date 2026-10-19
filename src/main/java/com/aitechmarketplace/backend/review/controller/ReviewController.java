package com.aitechmarketplace.backend.review.controller;

import com.aitechmarketplace.backend.common.dto.ApiResponse;
import com.aitechmarketplace.backend.review.dto.*;
import com.aitechmarketplace.backend.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/api/v1/products/{productId}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @PathVariable Long productId,
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody ReviewCreateRequest request
    ) {
        ReviewResponse response = reviewService.createReview(userId, productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Review submitted successfully"));
    }

    @PutMapping("/api/v1/reviews/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReview(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody ReviewUpdateRequest request
    ) {
        ReviewResponse response = reviewService.updateReview(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Review updated successfully"));
    }

    @GetMapping("/api/v1/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Boolean hasImages,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<ReviewResponse> response = reviewService.getProductReviews(productId, rating, hasImages, sort, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/products/{productId}/reviews/summary")
    public ResponseEntity<ApiResponse<ReviewSummaryResponse>> getReviewSummary(
            @PathVariable Long productId
    ) {
        ReviewSummaryResponse response = reviewService.getReviewSummary(productId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/api/v1/reviews/{id}/helpful")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<HelpfulVoteResponse>> toggleHelpfulVote(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId
    ) {
        HelpfulVoteResponse response = reviewService.toggleHelpfulVote(userId, id);
        return ResponseEntity.ok(ApiResponse.success(response, "Helpful vote recorded"));
    }

    @PostMapping("/api/v1/reviews/{id}/seller-reply")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SellerReplyResponse>> postSellerReply(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            Authentication authentication,
            @Valid @RequestBody SellerReplyRequest request
    ) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        SellerReplyResponse response = reviewService.postSellerReply(userId, isAdmin, id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Seller reply posted successfully"));
    }
}
