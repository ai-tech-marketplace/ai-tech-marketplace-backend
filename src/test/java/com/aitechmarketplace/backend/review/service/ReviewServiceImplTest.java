package com.aitechmarketplace.backend.review.service;

import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.ForbiddenException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.OrderItem;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import com.aitechmarketplace.backend.order.repository.OrderItemRepository;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.review.dto.ReviewCreateRequest;
import com.aitechmarketplace.backend.review.dto.ReviewResponse;
import com.aitechmarketplace.backend.review.dto.ReviewUpdateRequest;
import com.aitechmarketplace.backend.review.entity.Review;
import com.aitechmarketplace.backend.review.entity.ReviewStatus;
import com.aitechmarketplace.backend.review.repository.ReviewHelpfulVoteRepository;
import com.aitechmarketplace.backend.review.repository.ReviewImageRepository;
import com.aitechmarketplace.backend.review.repository.ReviewRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewImageRepository reviewImageRepository;

    @Mock
    private ReviewHelpfulVoteRepository helpfulVoteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private User user;
    private Product product;
    private OrderItem orderItem;
    private Order order;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setFullName("John Doe");

        product = new Product();
        product.setId(10L);
        product.setName("Gaming Laptop");

        order = new Order();
        order.setId(100L);
        order.setUser(user);
        order.setStatus(OrderStatus.DELIVERED);

        orderItem = new OrderItem();
        orderItem.setId(50L);
        orderItem.setOrder(order);
        orderItem.setProduct(product);
        orderItem.setUnitPrice(new BigDecimal("1000.00"));
    }

    @Test
    void createReview_success() {
        ReviewCreateRequest request = new ReviewCreateRequest(50L, 5, "Great product", List.of("https://s3.example.com/img1.webp"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(orderItemRepository.findById(50L)).thenReturn(Optional.of(orderItem));
        when(reviewRepository.existsByUserIdAndProductIdAndOrderItemId(1L, 10L, 50L)).thenReturn(false);

        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(999L);
            return r;
        });

        ReviewResponse response = reviewService.createReview(1L, 10L, request);

        assertThat(response).isNotNull();
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.comment()).isEqualTo("Great product");
        assertThat(response.images()).hasSize(1);
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    void createReview_orderNotDelivered_shouldThrowBadRequest() {
        order.setStatus(OrderStatus.SHIPPED);
        ReviewCreateRequest request = new ReviewCreateRequest(50L, 5, "Great product", null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(orderItemRepository.findById(50L)).thenReturn(Optional.of(orderItem));

        assertThatThrownBy(() -> reviewService.createReview(1L, 10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ORDER_NOT_DELIVERED");
    }

    @Test
    void createReview_notVerifiedPurchase_shouldThrowBadRequest() {
        User otherUser = new User();
        otherUser.setId(2L);
        order.setUser(otherUser);
        ReviewCreateRequest request = new ReviewCreateRequest(50L, 5, "Great product", null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(orderItemRepository.findById(50L)).thenReturn(Optional.of(orderItem));

        assertThatThrownBy(() -> reviewService.createReview(1L, 10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("NOT_VERIFIED_PURCHASE");
    }

    @Test
    void createReview_duplicateReview_shouldThrowConflict() {
        ReviewCreateRequest request = new ReviewCreateRequest(50L, 5, "Great product", null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(orderItemRepository.findById(50L)).thenReturn(Optional.of(orderItem));
        when(reviewRepository.existsByUserIdAndProductIdAndOrderItemId(1L, 10L, 50L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(1L, 10L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("REVIEW_ALREADY_EXISTS");
    }

    @Test
    void updateReview_successWithin30Days() {
        Review review = new Review(user, product, orderItem, 5, "Old comment");
        review.setId(999L);
        review.setCreatedAt(OffsetDateTime.now().minusDays(5));

        ReviewUpdateRequest updateRequest = new ReviewUpdateRequest(4, "Updated comment", null);

        when(reviewRepository.findById(999L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponse response = reviewService.updateReview(1L, 999L, updateRequest);

        assertThat(response.rating()).isEqualTo(4);
        assertThat(response.comment()).isEqualTo("Updated comment");
        assertThat(response.isEdited()).isTrue();
    }

    @Test
    void updateReview_expiredWindow_shouldThrowBadRequest() {
        Review review = new Review(user, product, orderItem, 5, "Old comment");
        review.setId(999L);
        review.setCreatedAt(OffsetDateTime.now().minusDays(35));

        ReviewUpdateRequest updateRequest = new ReviewUpdateRequest(4, "Updated comment", null);

        when(reviewRepository.findById(999L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.updateReview(1L, 999L, updateRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("REVIEW_EDIT_WINDOW_EXPIRED");
    }

    @Test
    void updateReview_differentUser_shouldThrowForbidden() {
        Review review = new Review(user, product, orderItem, 5, "Old comment");
        review.setId(999L);
        review.setCreatedAt(OffsetDateTime.now().minusDays(5));

        ReviewUpdateRequest updateRequest = new ReviewUpdateRequest(4, "Updated comment", null);

        when(reviewRepository.findById(999L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.updateReview(2L, 999L, updateRequest))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("only edit your own review");
    }
}
