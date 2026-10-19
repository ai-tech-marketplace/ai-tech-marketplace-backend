package com.aitechmarketplace.backend.review.controller;

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
import com.aitechmarketplace.backend.review.entity.ReviewImage;
import com.aitechmarketplace.backend.review.entity.ReviewStatus;
import com.aitechmarketplace.backend.review.repository.ReviewHelpfulVoteRepository;
import com.aitechmarketplace.backend.review.repository.ReviewImageRepository;
import com.aitechmarketplace.backend.review.repository.ReviewRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PublicReviewControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewImageRepository reviewImageRepository;

    @Autowired
    private ReviewHelpfulVoteRepository helpfulVoteRepository;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdBrandIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();
    private final List<Long> createdOrderIds = new ArrayList<>();

    private Product product;

    @BeforeEach
    void setUp() {
        User seller = createUser("Seller " + UUID.randomUUID());
        User buyer1 = createUser("Alice " + UUID.randomUUID());
        User buyer2 = createUser("Bob " + UUID.randomUUID());
        User buyer3 = createUser("Charlie " + UUID.randomUUID());

        Category category = new Category();
        category.setName("Category " + UUID.randomUUID());
        category.setSlug("cat-" + UUID.randomUUID());
        category = categoryRepository.save(category);
        createdCategoryIds.add(category.getId());

        Brand brand = new Brand();
        brand.setName("Brand " + UUID.randomUUID());
        brand.setSlug("brand-" + UUID.randomUUID());
        brand = brandRepository.save(brand);
        createdBrandIds.add(brand.getId());

        Product p = new Product();
        p.setSeller(seller);
        p.setCategory(category);
        p.setBrand(brand);
        p.setName("Wireless Noise Cancelling Headphones");
        p.setSlug("headphones-" + UUID.randomUUID());
        p.setSku("HP-" + UUID.randomUUID().toString().substring(0, 8));
        p.setPrice(new BigDecimal("350.00"));
        p.setStockQuantity(50);
        p.setStatus("ACTIVE");
        product = productRepository.save(p);
        createdProductIds.add(product.getId());

        // Create 3 orders and items
        OrderItem item1 = createDeliveredOrderItem(buyer1, product);
        OrderItem item2 = createDeliveredOrderItem(buyer2, product);
        OrderItem item3 = createDeliveredOrderItem(buyer3, product);

        // Review 1: 5 stars with image, helpfulCount = 10
        Review review1 = new Review(buyer1, product, item1, 5, "Sound quality is amazing!");
        review1.setHelpfulCount(10);
        review1.addImage(new ReviewImage("https://s3.example.com/hp1.webp", 0));
        reviewRepository.save(review1);

        // Review 2: 4 stars without image, helpfulCount = 2
        Review review2 = new Review(buyer2, product, item2, 4, "Great battery life.");
        review2.setHelpfulCount(2);
        reviewRepository.save(review2);

        // Review 3: 1 star, HIDDEN by admin
        Review review3 = new Review(buyer3, product, item3, 1, "Offensive text");
        review3.setStatus(ReviewStatus.HIDDEN);
        review3.setHiddenReason("Violates community guidelines");
        reviewRepository.save(review3);
    }

    @AfterEach
    void tearDown() {
        helpfulVoteRepository.deleteAll();
        reviewImageRepository.deleteAll();
        reviewRepository.deleteAll();

        orderItemRepository.deleteAll();
        createdOrderIds.forEach(id -> {
            if (orderRepository.existsById(id)) orderRepository.deleteById(id);
        });
        createdOrderIds.clear();

        createdProductIds.forEach(id -> {
            if (productRepository.existsById(id)) productRepository.deleteById(id);
        });
        createdProductIds.clear();

        createdBrandIds.forEach(id -> {
            if (brandRepository.existsById(id)) brandRepository.deleteById(id);
        });
        createdBrandIds.clear();

        createdCategoryIds.forEach(id -> {
            if (categoryRepository.existsById(id)) categoryRepository.deleteById(id);
        });
        createdCategoryIds.clear();

        createdUserIds.forEach(id -> {
            if (userRepository.existsById(id)) userRepository.deleteById(id);
        });
        createdUserIds.clear();
    }

    @Test
    void getProductReviews_publicListing_excludesHiddenReviews() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + product.getId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements", is(2)));
    }

    @Test
    void getProductReviews_filterByRatingAndHasImages() throws Exception {
        // Filter rating = 5
        mockMvc.perform(get("/api/v1/products/" + product.getId() + "/reviews")
                        .param("rating", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].rating", is(5)));

        // Filter hasImages = true
        mockMvc.perform(get("/api/v1/products/" + product.getId() + "/reviews")
                        .param("hasImages", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].images", hasSize(1)));
    }

    @Test
    void getProductReviews_sortByMostHelpful() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + product.getId() + "/reviews")
                        .param("sort", "most_helpful"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].helpfulCount", is(10)))
                .andExpect(jsonPath("$.data.content[1].helpfulCount", is(2)));
    }

    @Test
    void getReviewSummary_success() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + product.getId() + "/reviews/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.productId", is(product.getId().intValue())))
                .andExpect(jsonPath("$.data.totalReviews", is(2)))
                .andExpect(jsonPath("$.data.averageRating", is(4.5)))
                .andExpect(jsonPath("$.data.reviewsWithImagesCount", is(1)))
                .andExpect(jsonPath("$.data.ratingDistribution.5", is(1)))
                .andExpect(jsonPath("$.data.ratingDistribution.4", is(1)))
                .andExpect(jsonPath("$.data.ratingDistribution.3", is(0)))
                .andExpect(jsonPath("$.data.ratingDistribution.2", is(0)))
                .andExpect(jsonPath("$.data.ratingDistribution.1", is(0)));
    }

    @Test
    void getReviewSummary_nonExistentProduct_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/products/999999/reviews/summary"))
                .andExpect(status().isNotFound());
    }

    private User createUser(String name) {
        User u = new User();
        u.setEmail("user-" + UUID.randomUUID() + "@example.com");
        u.setPasswordHash("hash");
        u.setFullName(name);
        u = userRepository.save(u);
        createdUserIds.add(u.getId());
        return u;
    }

    private OrderItem createDeliveredOrderItem(User buyer, Product prod) {
        Order order = new Order();
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8));
        order.setUser(buyer);
        order.setStatus(OrderStatus.DELIVERED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setSubtotal(prod.getPrice());
        order.setTotalAmount(prod.getPrice());
        order.setShippingRecipientName("Recipient");
        order.setShippingPhone("0123456789");
        order.setShippingAddress("Address");
        Order savedOrder = orderRepository.save(order);
        createdOrderIds.add(savedOrder.getId());

        OrderItem item = new OrderItem(savedOrder, prod, prod.getName(), prod.getSku(), 1, prod.getPrice(), prod.getPrice());
        return orderItemRepository.save(item);
    }
}
