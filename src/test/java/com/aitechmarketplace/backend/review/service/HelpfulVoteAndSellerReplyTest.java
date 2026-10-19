package com.aitechmarketplace.backend.review.service;

import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.ForbiddenException;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.review.dto.HelpfulVoteResponse;
import com.aitechmarketplace.backend.review.dto.SellerReplyRequest;
import com.aitechmarketplace.backend.review.dto.SellerReplyResponse;
import com.aitechmarketplace.backend.review.entity.Review;
import com.aitechmarketplace.backend.review.entity.ReviewHelpfulVote;
import com.aitechmarketplace.backend.review.entity.ReviewStatus;
import com.aitechmarketplace.backend.review.repository.ReviewHelpfulVoteRepository;
import com.aitechmarketplace.backend.review.repository.ReviewRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HelpfulVoteAndSellerReplyTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewHelpfulVoteRepository helpfulVoteRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private User author;
    private User voter;
    private User seller;
    private User otherSeller;
    private Product product;
    private Review review;

    @BeforeEach
    void setUp() {
        author = new User();
        author.setId(1L);

        voter = new User();
        voter.setId(2L);

        seller = new User();
        seller.setId(10L);

        otherSeller = new User();
        otherSeller.setId(20L);

        product = new Product();
        product.setId(100L);
        product.setSeller(seller);

        review = new Review();
        review.setId(50L);
        review.setUser(author);
        review.setProduct(product);
        review.setStatus(ReviewStatus.VISIBLE);
        review.setHelpfulCount(0);
    }

    @Test
    void toggleHelpfulVote_selfVote_shouldThrowBadRequest() {
        when(reviewRepository.findByIdAndStatus(50L, ReviewStatus.VISIBLE)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.toggleHelpfulVote(1L, 50L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CANNOT_VOTE_OWN_REVIEW");
    }

    @Test
    void toggleHelpfulVote_firstVote_shouldAddVoteAndIncrementCount() {
        when(reviewRepository.findByIdAndStatus(50L, ReviewStatus.VISIBLE)).thenReturn(Optional.of(review));
        when(userRepository.findById(2L)).thenReturn(Optional.of(voter));
        when(helpfulVoteRepository.existsByReviewIdAndUserId(50L, 2L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        HelpfulVoteResponse response = reviewService.toggleHelpfulVote(2L, 50L);

        assertThat(response.voted()).isTrue();
        assertThat(response.helpfulCount()).isEqualTo(1);
        verify(helpfulVoteRepository).save(any(ReviewHelpfulVote.class));
    }

    @Test
    void toggleHelpfulVote_secondVote_shouldRemoveVoteAndDecrementCount() {
        review.setHelpfulCount(1);
        when(reviewRepository.findByIdAndStatus(50L, ReviewStatus.VISIBLE)).thenReturn(Optional.of(review));
        when(userRepository.findById(2L)).thenReturn(Optional.of(voter));
        when(helpfulVoteRepository.existsByReviewIdAndUserId(50L, 2L)).thenReturn(true);
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        HelpfulVoteResponse response = reviewService.toggleHelpfulVote(2L, 50L);

        assertThat(response.voted()).isFalse();
        assertThat(response.helpfulCount()).isEqualTo(0);
        verify(helpfulVoteRepository).deleteByReviewIdAndUserId(50L, 2L);
    }

    @Test
    void postSellerReply_ownerSeller_success() {
        SellerReplyRequest request = new SellerReplyRequest("Thank you for your feedback!");

        when(reviewRepository.findById(50L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        SellerReplyResponse response = reviewService.postSellerReply(10L, false, 50L, request);

        assertThat(response.sellerReply()).isEqualTo("Thank you for your feedback!");
        assertThat(response.sellerRepliedAt()).isNotNull();
    }

    @Test
    void postSellerReply_otherSeller_shouldThrowForbidden() {
        SellerReplyRequest request = new SellerReplyRequest("Thank you!");

        when(reviewRepository.findById(50L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.postSellerReply(20L, false, 50L, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("FORBIDDEN_SELLER_REPLY");
    }

    @Test
    void postSellerReply_admin_success() {
        SellerReplyRequest request = new SellerReplyRequest("Admin resolution response");

        when(reviewRepository.findById(50L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        SellerReplyResponse response = reviewService.postSellerReply(999L, true, 50L, request);

        assertThat(response.sellerReply()).isEqualTo("Admin resolution response");
    }

    @Test
    void postSellerReply_alreadyReplied_shouldThrowConflict() {
        review.setSellerReply("Existing reply");
        SellerReplyRequest request = new SellerReplyRequest("Second reply");

        when(reviewRepository.findById(50L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.postSellerReply(10L, false, 50L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("SELLER_ALREADY_REPLIED");
    }
}
