package com.aitechmarketplace.backend.review.repository;

import com.aitechmarketplace.backend.review.entity.ReviewHelpfulVote;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ReviewHelpfulVoteRepository extends JpaRepository<ReviewHelpfulVote, Long> {
    Optional<ReviewHelpfulVote> findByReviewIdAndUserId(Long reviewId, Long userId);
    boolean existsByReviewIdAndUserId(Long reviewId, Long userId);

    @Transactional
    void deleteByReviewIdAndUserId(Long reviewId, Long userId);
}
