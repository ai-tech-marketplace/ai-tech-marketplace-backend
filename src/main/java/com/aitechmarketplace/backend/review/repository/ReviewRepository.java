package com.aitechmarketplace.backend.review.repository;

import com.aitechmarketplace.backend.review.entity.Review;
import com.aitechmarketplace.backend.review.entity.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> {

    @EntityGraph(attributePaths = {"images", "user"})
    Optional<Review> findByIdAndStatus(Long id, ReviewStatus status);

    boolean existsByUserIdAndProductIdAndOrderItemId(Long userId, Long productId, Long orderItemId);

    @EntityGraph(attributePaths = {"images", "user"})
    Page<Review> findByProductIdAndStatus(Long productId, ReviewStatus status, Pageable pageable);

    long countByProductIdAndStatus(Long productId, ReviewStatus status);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId AND r.status = :status")
    Double getAverageRatingByProductIdAndStatus(@Param("productId") Long productId, @Param("status") ReviewStatus status);

    @Query("SELECT COUNT(DISTINCT r.id) FROM Review r JOIN r.images img WHERE r.product.id = :productId AND r.status = :status")
    long countReviewsWithImages(@Param("productId") Long productId, @Param("status") ReviewStatus status);

    @Query("SELECT r.rating AS rating, COUNT(r.id) AS count FROM Review r WHERE r.product.id = :productId AND r.status = :status GROUP BY r.rating")
    List<RatingDistributionProjection> getRatingDistributionByProductIdAndStatus(@Param("productId") Long productId, @Param("status") ReviewStatus status);
}
