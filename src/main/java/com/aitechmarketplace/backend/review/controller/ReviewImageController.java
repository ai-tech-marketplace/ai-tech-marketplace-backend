package com.aitechmarketplace.backend.review.controller;

import com.aitechmarketplace.backend.common.dto.ApiResponse;
import com.aitechmarketplace.backend.review.dto.ReviewImageUploadResponse;
import com.aitechmarketplace.backend.review.service.ReviewImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/reviews/images")
public class ReviewImageController {

    private final ReviewImageService reviewImageService;

    public ReviewImageController(ReviewImageService reviewImageService) {
        this.reviewImageService = reviewImageService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewImageUploadResponse>> upload(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        ReviewImageUploadResponse response = reviewImageService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Image uploaded successfully"));
    }
}
