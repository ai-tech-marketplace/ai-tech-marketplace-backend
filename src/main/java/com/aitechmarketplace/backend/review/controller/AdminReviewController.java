package com.aitechmarketplace.backend.review.controller;

import com.aitechmarketplace.backend.common.dto.ApiResponse;
import com.aitechmarketplace.backend.review.dto.AdminReviewModerationRequest;
import com.aitechmarketplace.backend.review.dto.AdminReviewModerationResponse;
import com.aitechmarketplace.backend.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminReviewModerationResponse>> moderateReview(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody AdminReviewModerationRequest request
    ) {
        AdminReviewModerationResponse response = reviewService.moderateReview(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Review status updated by admin"));
    }
}
