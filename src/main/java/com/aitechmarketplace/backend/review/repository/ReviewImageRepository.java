package com.aitechmarketplace.backend.review.repository;

import com.aitechmarketplace.backend.review.entity.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {
    List<ReviewImage> findByReviewIdOrderByDisplayOrderAsc(Long reviewId);

    @Transactional
    void deleteByReviewId(Long reviewId);
}
