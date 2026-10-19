package com.aitechmarketplace.backend.review.repository;

import com.aitechmarketplace.backend.brand.entity.Brand;
import com.aitechmarketplace.backend.brand.repository.BrandRepository;
import com.aitechmarketplace.backend.category.entity.Category;
import com.aitechmarketplace.backend.category.repository.CategoryRepository;
import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.OrderItem;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.order.entity.PaymentStatus;
import com.aitechmarketplace.backend.order.repository.OrderItemRepository;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.review.entity.Review;
import com.aitechmarketplace.backend.review.entity.ReviewHelpfulVote;
import com.aitechmarketplace.backend.review.entity.ReviewImage;
import com.aitechmarketplace.backend.review.entity.ReviewStatus;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ReviewRepositoryTest {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewImageRepository reviewImageRepository;

    @Autowired
    private ReviewHelpfulVoteRepository helpfulVoteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    private User customer;
    private User seller;
    private Product product;
    private OrderItem orderItem;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setEmail("cust_" + UUID.randomUUID() + "@example.com");
        customer.setPasswordHash("hash");
        customer.setFullName("Customer User");
        customer = userRepository.save(customer);

        seller = new User();
        seller.setEmail("seller_" + UUID.randomUUID() + "@example.com");
        seller.setPasswordHash("hash");
        seller.setFullName("Seller User");
        seller = userRepository.save(seller);

        Category category = new Category();
        category.setName("Category " + UUID.randomUUID());
        category.setSlug("cat-" + UUID.randomUUID());
        category = categoryRepository.save(category);

        Brand brand = new Brand();
        brand.setName("Brand " + UUID.randomUUID());
        brand.setSlug("brand-" + UUID.randomUUID());
        brand = brandRepository.save(brand);

        Product p = new Product();
        p.setSeller(seller);
        p.setCategory(category);
        p.setBrand(brand);
        p.setName("Asus ROG Strix");
        p.setSlug("asus-rog-strix-" + UUID.randomUUID());
        p.setSku("ROG-" + UUID.randomUUID().toString().substring(0, 8));
        p.setPrice(new BigDecimal("1500.00"));
        p.setStockQuantity(10);
        p.setStatus("ACTIVE");
        product = productRepository.save(p);

        Order order = new Order();
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8));
        order.setUser(customer);
        order.setStatus(OrderStatus.DELIVERED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setSubtotal(new BigDecimal("1500.00"));
        order.setTotalAmount(new BigDecimal("1500.00"));
        order.setShippingRecipientName("Customer");
        order.setShippingPhone("0123456789");
        order.setShippingAddress("123 Street");
        Order savedOrder = orderRepository.save(order);

        orderItem = new OrderItem(savedOrder, product, product.getName(), product.getSku(), 1, product.getPrice(), product.getPrice());
        orderItem = orderItemRepository.save(orderItem);
    }

    @AfterEach
    void tearDown() {
        helpfulVoteRepository.deleteAll();
        reviewImageRepository.deleteAll();
        reviewRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        brandRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldSaveAndQueryReviewWithImages() {
        Review review = new Review(customer, product, orderItem, 5, "Amazing laptop!");
        review.addImage(new ReviewImage("https://s3.example.com/img1.webp", 0));
        review.addImage(new ReviewImage("https://s3.example.com/img2.webp", 1));

        Review saved = reviewRepository.save(review);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getImages()).hasSize(2);

        Page<Review> page = reviewRepository.findByProductIdAndStatus(product.getId(), ReviewStatus.VISIBLE, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getComment()).isEqualTo("Amazing laptop!");
        assertThat(page.getContent().get(0).getImages()).hasSize(2);
    }

    @Test
    void shouldEnforceUniqueReviewPerOrderItem() {
        Review review1 = new Review(customer, product, orderItem, 5, "First review");
        reviewRepository.saveAndFlush(review1);

        Review review2 = new Review(customer, product, orderItem, 4, "Duplicate review");
        assertThatThrownBy(() -> reviewRepository.saveAndFlush(review2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldCalculateAverageRatingAndCountWithImages() {
        Review review1 = new Review(customer, product, orderItem, 5, "First review");
        review1.addImage(new ReviewImage("https://s3.example.com/img1.webp", 0));
        reviewRepository.save(review1);

        Double avgRating = reviewRepository.getAverageRatingByProductIdAndStatus(product.getId(), ReviewStatus.VISIBLE);
        assertThat(avgRating).isEqualTo(5.0);

        long withImages = reviewRepository.countReviewsWithImages(product.getId(), ReviewStatus.VISIBLE);
        assertThat(withImages).isEqualTo(1);

        List<RatingDistributionProjection> distribution = reviewRepository.getRatingDistributionByProductIdAndStatus(product.getId(), ReviewStatus.VISIBLE);
        assertThat(distribution).hasSize(1);
        assertThat(distribution.get(0).getRating()).isEqualTo(5);
        assertThat(distribution.get(0).getCount()).isEqualTo(1L);
    }

    @Test
    void shouldSaveAndToggleHelpfulVote() {
        Review review = reviewRepository.save(new Review(customer, product, orderItem, 5, "Helpful review"));

        ReviewHelpfulVote vote = new ReviewHelpfulVote(review, seller);
        helpfulVoteRepository.save(vote);

        assertThat(helpfulVoteRepository.existsByReviewIdAndUserId(review.getId(), seller.getId())).isTrue();

        helpfulVoteRepository.deleteByReviewIdAndUserId(review.getId(), seller.getId());
        assertThat(helpfulVoteRepository.existsByReviewIdAndUserId(review.getId(), seller.getId())).isFalse();
    }
}
