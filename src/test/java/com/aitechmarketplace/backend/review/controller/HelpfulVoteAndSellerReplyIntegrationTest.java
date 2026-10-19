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
import com.aitechmarketplace.backend.review.repository.ReviewHelpfulVoteRepository;
import com.aitechmarketplace.backend.review.repository.ReviewImageRepository;
import com.aitechmarketplace.backend.review.repository.ReviewRepository;
import com.aitechmarketplace.backend.user.entity.Role;
import com.aitechmarketplace.backend.user.entity.RoleName;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.RoleRepository;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import com.aitechmarketplace.backend.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HelpfulVoteAndSellerReplyIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdBrandIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();
    private final List<Long> createdOrderIds = new ArrayList<>();

    private User author;
    private String authorToken;
    private User otherUser;
    private String otherUserToken;
    private User seller;
    private String sellerToken;
    private User anotherSeller;
    private String anotherSellerToken;

    private Review review;

    @BeforeEach
    void setUp() throws Exception {
        author = registerUser("author", "SELLER");
        authorToken = login(author.getEmail());

        otherUser = registerUser("voter", "CUSTOMER");
        otherUserToken = login(otherUser.getEmail());

        seller = registerUser("seller", "SELLER");
        sellerToken = login(seller.getEmail());

        anotherSeller = registerUser("otherseller", "SELLER");
        anotherSellerToken = login(anotherSeller.getEmail());

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

        Product product = new Product();
        product.setSeller(seller);
        product.setCategory(category);
        product.setBrand(brand);
        product.setName("Tech Gadget");
        product.setSlug("tech-gadget-" + UUID.randomUUID());
        product.setSku("TG-" + UUID.randomUUID().toString().substring(0, 8));
        product.setPrice(new BigDecimal("200.00"));
        product.setStockQuantity(10);
        product.setStatus("ACTIVE");
        product = productRepository.save(product);
        createdProductIds.add(product.getId());

        Order order = new Order();
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8));
        order.setUser(author);
        order.setStatus(OrderStatus.DELIVERED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setSubtotal(product.getPrice());
        order.setTotalAmount(product.getPrice());
        order.setShippingRecipientName("Recipient");
        order.setShippingPhone("0123456789");
        order.setShippingAddress("Street");
        Order savedOrder = orderRepository.save(order);
        createdOrderIds.add(savedOrder.getId());

        OrderItem orderItem = new OrderItem(savedOrder, product, product.getName(), product.getSku(), 1, product.getPrice(), product.getPrice());
        orderItem = orderItemRepository.save(orderItem);

        review = new Review(author, product, orderItem, 5, "Super handy gadget!");
        review = reviewRepository.save(review);
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
    void toggleHelpfulVote_flow() throws Exception {
        // Author self-voting should be rejected with 400
        mockMvc.perform(
                post("/api/v1/reviews/" + review.getId() + "/helpful")
                        .header("Authorization", "Bearer " + authorToken)
        )
                .andExpect(status().isBadRequest());

        // Other user voting should succeed
        mockMvc.perform(
                post("/api/v1/reviews/" + review.getId() + "/helpful")
                        .header("Authorization", "Bearer " + otherUserToken)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.voted", is(true)))
                .andExpect(jsonPath("$.data.helpfulCount", is(1)));

        // Other user voting again should toggle off
        mockMvc.perform(
                post("/api/v1/reviews/" + review.getId() + "/helpful")
                        .header("Authorization", "Bearer " + otherUserToken)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.voted", is(false)))
                .andExpect(jsonPath("$.data.helpfulCount", is(0)));
    }

    @Test
    void sellerReply_flow() throws Exception {
        String replyPayload = """
                {
                    "reply": "Cảm ơn bạn đã ủng hộ shop!"
                }
                """;

        // Another seller trying to reply should get 403 Forbidden
        mockMvc.perform(
                post("/api/v1/reviews/" + review.getId() + "/seller-reply")
                        .header("Authorization", "Bearer " + anotherSellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyPayload)
        )
                .andExpect(status().isForbidden());

        // Owner seller replying should succeed
        mockMvc.perform(
                post("/api/v1/reviews/" + review.getId() + "/seller-reply")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyPayload)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.sellerReply", is("Cảm ơn bạn đã ủng hộ shop!")))
                .andExpect(jsonPath("$.data.sellerRepliedAt").isNotEmpty());

        // Duplicate reply should fail with 409 Conflict
        mockMvc.perform(
                post("/api/v1/reviews/" + review.getId() + "/seller-reply")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyPayload)
        )
                .andExpect(status().isConflict());
    }

    private User registerUser(String prefix, String role) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        String passwordHash = passwordEncoder.encode(PASSWORD);
        User user = userService.register(email, passwordHash, prefix + " name", null);
        if ("SELLER".equalsIgnoreCase(role)) {
            Role sellerRole = roleRepository.findByName(RoleName.SELLER).orElseThrow();
            user.getRoles().add(sellerRole);
            user = userRepository.save(user);
        } else if ("ADMIN".equalsIgnoreCase(role)) {
            Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
            user.getRoles().add(adminRole);
            user = userRepository.save(user);
        }
        createdUserIds.add(user.getId());
        return user;
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(email, PASSWORD))
        )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.path("data").path("accessToken").asText();
    }
}
