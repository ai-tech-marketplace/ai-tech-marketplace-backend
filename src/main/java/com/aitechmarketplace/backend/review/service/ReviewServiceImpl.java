package com.aitechmarketplace.backend.review.service;

import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.ForbiddenException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.order.entity.OrderItem;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import com.aitechmarketplace.backend.order.repository.OrderItemRepository;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.review.dto.*;
import com.aitechmarketplace.backend.review.entity.Review;
import com.aitechmarketplace.backend.review.entity.ReviewHelpfulVote;
import com.aitechmarketplace.backend.review.entity.ReviewImage;
import com.aitechmarketplace.backend.review.entity.ReviewStatus;
import com.aitechmarketplace.backend.review.repository.RatingDistributionProjection;
import com.aitechmarketplace.backend.review.repository.ReviewHelpfulVoteRepository;
import com.aitechmarketplace.backend.review.repository.ReviewImageRepository;
import com.aitechmarketplace.backend.review.repository.ReviewRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ReviewHelpfulVoteRepository helpfulVoteRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    public ReviewServiceImpl(
            ReviewRepository reviewRepository,
            ReviewImageRepository reviewImageRepository,
            ReviewHelpfulVoteRepository helpfulVoteRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            OrderItemRepository orderItemRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.reviewImageRepository = reviewImageRepository;
        this.helpfulVoteRepository = helpfulVoteRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Override
    public ReviewResponse createReview(Long userId, Long productId, ReviewCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        OrderItem orderItem = orderItemRepository.findById(request.orderItemId())
                .orElseThrow(() -> new NotFoundException("Order item not found"));

        if (!orderItem.getProduct().getId().equals(productId)) {
            throw new BadRequestException("Order item does not belong to specified product");
        }

        if (!orderItem.getOrder().getUser().getId().equals(userId)) {
            throw new BadRequestException("NOT_VERIFIED_PURCHASE");
        }

        if (orderItem.getOrder().getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("ORDER_NOT_DELIVERED");
        }

        if (reviewRepository.existsByUserIdAndProductIdAndOrderItemId(userId, productId, request.orderItemId())) {
            throw new ConflictException("REVIEW_ALREADY_EXISTS");
        }

        if (request.imageUrls() != null && request.imageUrls().size() > 5) {
            throw new BadRequestException("Maximum 5 images allowed per review");
        }

        Review review = new Review(user, product, orderItem, request.rating(), request.comment());

        if (request.imageUrls() != null && !request.imageUrls().isEmpty()) {
            for (int i = 0; i < request.imageUrls().size(); i++) {
                review.addImage(new ReviewImage(request.imageUrls().get(i), i));
            }
        }

        Review saved = reviewRepository.save(review);
        return ReviewResponse.from(saved);
    }

    @Override
    public ReviewResponse updateReview(Long userId, Long reviewId, ReviewUpdateRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Review not found"));

        if (!review.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You can only edit your own review");
        }

        long daysSinceCreation = Duration.between(review.getCreatedAt(), OffsetDateTime.now()).toDays();
        if (daysSinceCreation > 30) {
            throw new BadRequestException("REVIEW_EDIT_WINDOW_EXPIRED");
        }

        if (request.imageUrls() != null && request.imageUrls().size() > 5) {
            throw new BadRequestException("Maximum 5 images allowed per review");
        }

        review.setRating(request.rating());
        review.setComment(request.comment());
        review.setIsEdited(true);

        if (request.imageUrls() != null) {
            review.clearImages();
            for (int i = 0; i < request.imageUrls().size(); i++) {
                review.addImage(new ReviewImage(request.imageUrls().get(i), i));
            }
        }

        Review saved = reviewRepository.save(review);
        return ReviewResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getProductReviews(Long productId, Integer rating, Boolean hasImages, String sort, Pageable pageable) {
        if (!productRepository.existsById(productId)) {
            throw new NotFoundException("Product not found");
        }

        Specification<Review> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("product").get("id"), productId));
            predicates.add(cb.equal(root.get("status"), ReviewStatus.VISIBLE));

            if (rating != null) {
                predicates.add(cb.equal(root.get("rating"), rating));
            }
            if (Boolean.TRUE.equals(hasImages)) {
                predicates.add(cb.isNotEmpty(root.get("images")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Sort sortOrder = switch (sort != null ? sort.toLowerCase() : "newest") {
            case "highest_rating" -> Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "lowest_rating" -> Sort.by(Sort.Direction.ASC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "most_helpful" -> Sort.by(Sort.Direction.DESC, "helpfulCount").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortOrder);
        return reviewRepository.findAll(spec, sortedPageable).map(ReviewResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewSummaryResponse getReviewSummary(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new NotFoundException("Product not found");
        }

        long totalReviews = reviewRepository.countByProductIdAndStatus(productId, ReviewStatus.VISIBLE);

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) {
            distribution.put(i, 0L);
        }

        if (totalReviews == 0) {
            return new ReviewSummaryResponse(productId, 0.0, 0L, 0L, distribution);
        }

        Double avgRaw = reviewRepository.getAverageRatingByProductIdAndStatus(productId, ReviewStatus.VISIBLE);
        double averageRating = avgRaw != null
                ? BigDecimal.valueOf(avgRaw).setScale(1, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        long reviewsWithImagesCount = reviewRepository.countReviewsWithImages(productId, ReviewStatus.VISIBLE);

        List<RatingDistributionProjection> projections = reviewRepository.getRatingDistributionByProductIdAndStatus(productId, ReviewStatus.VISIBLE);
        for (RatingDistributionProjection p : projections) {
            if (p.getRating() != null && distribution.containsKey(p.getRating())) {
                distribution.put(p.getRating(), p.getCount() != null ? p.getCount() : 0L);
            }
        }

        return new ReviewSummaryResponse(productId, averageRating, totalReviews, reviewsWithImagesCount, distribution);
    }

    @Override
    public HelpfulVoteResponse toggleHelpfulVote(Long userId, Long reviewId) {
        Review review = reviewRepository.findByIdAndStatus(reviewId, ReviewStatus.VISIBLE)
                .orElseThrow(() -> new NotFoundException("Review not found"));

        if (review.getUser().getId().equals(userId)) {
            throw new BadRequestException("CANNOT_VOTE_OWN_REVIEW");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        boolean alreadyVoted = helpfulVoteRepository.existsByReviewIdAndUserId(reviewId, userId);

        if (alreadyVoted) {
            helpfulVoteRepository.deleteByReviewIdAndUserId(reviewId, userId);
            review.setHelpfulCount(Math.max(0, review.getHelpfulCount() - 1));
            reviewRepository.save(review);
            return new HelpfulVoteResponse(reviewId, review.getHelpfulCount(), false);
        } else {
            helpfulVoteRepository.save(new ReviewHelpfulVote(review, user));
            review.setHelpfulCount(review.getHelpfulCount() + 1);
            reviewRepository.save(review);
            return new HelpfulVoteResponse(reviewId, review.getHelpfulCount(), true);
        }
    }

    @Override
    public SellerReplyResponse postSellerReply(Long userId, boolean isAdmin, Long reviewId, SellerReplyRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Review not found"));

        if (!isAdmin && !review.getProduct().getSeller().getId().equals(userId)) {
            throw new ForbiddenException("FORBIDDEN_SELLER_REPLY");
        }

        if (review.getSellerReply() != null) {
            throw new ConflictException("SELLER_ALREADY_REPLIED");
        }

        review.setSellerReply(request.reply().trim());
        review.setSellerRepliedAt(OffsetDateTime.now());

        Review saved = reviewRepository.save(review);
        return new SellerReplyResponse(saved.getId(), saved.getSellerReply(), saved.getSellerRepliedAt());
    }

    @Override
    public AdminReviewModerationResponse moderateReview(Long adminId, Long reviewId, AdminReviewModerationRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Review not found"));

        if (request.status() == ReviewStatus.HIDDEN && (request.hiddenReason() == null || request.hiddenReason().isBlank())) {
            throw new BadRequestException("hiddenReason is required when hiding review");
        }

        review.setStatus(request.status());
        review.setHiddenReason(request.status() == ReviewStatus.HIDDEN ? request.hiddenReason() : null);

        Review saved = reviewRepository.save(review);
        return new AdminReviewModerationResponse(saved.getId(), saved.getStatus(), saved.getHiddenReason(), saved.getUpdatedAt());
    }
}
