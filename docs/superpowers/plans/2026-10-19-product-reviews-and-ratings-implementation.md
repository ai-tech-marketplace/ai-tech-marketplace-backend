# Product Reviews & Ratings System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a comprehensive Product Review and Rating ecosystem with Flyway migration `V11`, photo uploads (`review_images`), verified purchase validation on `DELIVERED` orders, customer review submission and 30-day editing window, public filtering and rating metric summaries, helpful upvotes (`review_helpful_votes`), merchant official replies (`seller_reply`), and administrative moderation (`status = HIDDEN`).

**Architecture:** Layered Spring Boot 4 architecture utilizing Spring Data JPA, multipart S3 storage integration (`S3StorageService`), transactional verified purchase and uniqueness enforcement (`@Transactional`), native database rating aggregations, and strict RBAC authorization with Spring Security (`ROLE_CUSTOMER`, `ROLE_SELLER`, `ROLE_ADMIN`).

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Data JPA, Hibernate, PostgreSQL 17, Flyway, Spring Security 6, AWS SDK v2 (S3/SeaweedFS), JUnit 5, Mockito, MockMvc, AssertJ.

**Spec:**
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/api/reviews.md`
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/database/schema.md` (Sections 18, 18.1, 18.2)
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/database/migrations.md` (V11)
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/requirements/business-rules.md` (BR-REVIEW-001 through BR-REVIEW-008)

---

## Global Constraints

- All REST endpoints must use prefix `/api/v1/...` (e.g., `/api/v1/products/{productId}/reviews`, `/api/v1/reviews/*`, `/api/v1/admin/reviews/*`).
- All success responses must wrap data in `ApiResponse<T>` (`success: true`, `data: T`, `message: String`, `timestamp: String`).
- All error responses must use `ErrorResponse` (`code: String`, `message: String`, `details: Object`, `timestamp: String`) handled via `GlobalExceptionHandler`.
- Rating must be an integer between 1 and 5 stars (`1 <= rating <= 5`).
- Review uniqueness: Exactly one review per user per purchased order item (`UNIQUE (user_id, product_id, order_item_id)`).
- Verified Purchase Requirement (`BR-REVIEW-001`): Order item must belong to the caller, match the product ID, and order status must be `OrderStatus.DELIVERED`.
- Editing Window (`BR-REVIEW-005`): Allowed only within 30 days of submission; sets `is_edited = true`.
- Media Uploads (`BR-REVIEW-004`): Maximum 5 photos per review, stored in S3/SeaweedFS, allowed extensions: JPEG, PNG, WEBP, max 5MB per file.
- Seller Reply (`BR-REVIEW-006`): Only the store owner (`product.seller_id == caller.id`) or Admin can post an official reply; maximum 1 reply per review.
- Helpful Votes (`BR-REVIEW-007`): Authenticated users can toggle helpful votes; users cannot upvote their own review (`CANNOT_VOTE_OWN_REVIEW`).
- Moderation (`BR-REVIEW-008`): Admin can set `status = HIDDEN` with required `hidden_reason`; hidden reviews are excluded from public lists, average rating, and rating distributions.
- All tests must pass cleanly via `./mvnw test`.

---

## Review Focus

1. **Verified Purchase Security**: Submitting a review for a product not purchased, or purchased in an order that is still `PENDING`, `PROCESSING`, `SHIPPED`, or `CANCELLED`, must be rejected with HTTP 400 Bad Request (`NOT_VERIFIED_PURCHASE`).
2. **Review Duplicate Rejection**: Attempting to post a second review for the same `order_item_id` must fail with HTTP 409 Conflict (`REVIEW_ALREADY_EXISTS`).
3. **Helpful Vote Self-Voting & Toggle**: A customer cannot upvote their own review; upvoting twice by the same user toggles the vote off and decrements `helpful_count` cleanly without duplicate key errors.
4. **Seller Reply Isolation**: A seller attempting to reply to a review on another seller's product must be denied with HTTP 403 Forbidden (`FORBIDDEN_SELLER_REPLY`).
5. **Hidden Review Calculation Exclusion**: Reviews flagged as `status = 'HIDDEN'` by admin must immediately be excluded from public `GET /reviews` lists and from `GET /reviews/summary` rating averages and count distributions.

---

### Task 1: Flyway Migration V11 & JPA Entities/Repositories

**Files:**
- Create: `src/main/resources/db/migration/V11__create_reviews.sql`
- Create: `src/main/java/com/aitechmarketplace/backend/review/entity/ReviewStatus.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/entity/Review.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/entity/ReviewImage.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/entity/ReviewHelpfulVote.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/repository/ReviewRepository.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/repository/ReviewImageRepository.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/repository/ReviewHelpfulVoteRepository.java`
- Test: `src/test/java/com/aitechmarketplace/backend/review/repository/ReviewRepositoryTest.java`

**Interfaces:**
- Produces: `ReviewStatus` enum: `VISIBLE`, `HIDDEN`.
- Produces: `Review` entity (`id`, `user`, `product`, `orderItem`, `rating`, `comment`, `isVerifiedPurchase`, `status`, `hiddenReason`, `sellerReply`, `sellerRepliedAt`, `helpfulCount`, `isEdited`, `createdAt`, `updatedAt`, `List<ReviewImage> images`).
- Produces: `ReviewImage` entity (`id`, `review`, `imageUrl`, `displayOrder`, `createdAt`).
- Produces: `ReviewHelpfulVote` entity (`id`, `review`, `user`, `createdAt`).
- Produces: `ReviewRepository`:
  - `Optional<Review> findByIdAndStatus(Long id, ReviewStatus status)`
  - `boolean existsByUserIdAndProductIdAndOrderItemId(Long userId, Long productId, Long orderItemId)`
  - Native/JPQL queries for filtered pagination and rating distribution projections.
- Produces: `ReviewHelpfulVoteRepository`:
  - `Optional<ReviewHelpfulVote> findByReviewIdAndUserId(Long reviewId, Long userId)`
  - `boolean existsByReviewIdAndUserId(Long reviewId, Long userId)`

- [ ] **Step 1: Write `V11__create_reviews.sql` migration**
  Create tables `reviews`, `review_images`, and `review_helpful_votes` with foreign keys, checks (`rating BETWEEN 1 AND 5`), unique constraints `(user_id, product_id, order_item_id)` and `(review_id, user_id)`, and performance indexes.
- [ ] **Step 2: Create Enums and Entities**
  Implement `ReviewStatus`, `Review`, `ReviewImage`, and `ReviewHelpfulVote` with JPA annotations.
- [ ] **Step 3: Create Spring Data Repositories**
  Define repository interfaces with query methods.
- [ ] **Step 4: Write repository tests**
  Test constraints, unique checks, foreign key cascading, and query executions.
  Run: `./mvnw test -Dtest=ReviewRepositoryTest`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add src/main/resources/db/migration/V11__create_reviews.sql src/main/java/com/aitechmarketplace/backend/review/ src/test/java/com/aitechmarketplace/backend/review/
  git commit -m "feat(db): add V11 migration and entities for reviews, images, and helpful votes"
  ```

---

### Task 2: Review Image Upload Service & Controller

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/review/dto/ReviewImageUploadResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/service/ReviewImageService.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/service/ReviewImageServiceImpl.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/controller/ReviewImageController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/review/service/ReviewImageServiceImplTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/review/controller/ReviewImageControllerIntegrationTest.java`

**Interfaces:**
- Produces: `ReviewImageService`:
  - `ReviewImageUploadResponse upload(MultipartFile file)`: Validates file type (image/jpeg, image/png, image/webp) and size ($\le 5$MB), generates UUID key, uploads to S3 under `reviews/{uuid}.{ext}`, returns public URL.
- Produces Endpoint:
  - `POST /api/v1/reviews/images` (multipart/form-data, `@PreAuthorize("isAuthenticated()")`)

- [ ] **Step 1: Create `ReviewImageUploadResponse` DTO**
- [ ] **Step 2: Write unit test for `ReviewImageServiceImpl`**
  Test file validation (reject pdf/exe, reject $> 5$MB) and S3 key generation.
- [ ] **Step 3: Implement `ReviewImageServiceImpl`**
- [ ] **Step 4: Implement `ReviewImageController`**
- [ ] **Step 5: Run integration tests**
  Run: `./mvnw test -Dtest=ReviewImageControllerIntegrationTest,ReviewImageServiceImplTest`
  Expected: PASS
- [ ] **Step 6: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/review/ src/test/java/com/aitechmarketplace/backend/review/
  git commit -m "feat(review): implement review image upload API with S3 storage"
  ```

---

### Task 3: Customer Review Submission & 30-Day Editing Module

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/review/dto/ReviewCreateRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/dto/ReviewUpdateRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/dto/ReviewImageResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/dto/ReviewResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/service/ReviewService.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/service/ReviewServiceImpl.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/controller/ReviewController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/review/service/ReviewServiceImplTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/review/controller/ReviewSubmissionIntegrationTest.java`

**Interfaces:**
- Produces: `ReviewService`:
  - `ReviewResponse createReview(Long userId, Long productId, ReviewCreateRequest request)`
  - `ReviewResponse updateReview(Long userId, Long reviewId, ReviewUpdateRequest request)`
- Produces Endpoints:
  - `POST /api/v1/products/{productId}/reviews` (`ROLE_CUSTOMER`)
  - `PUT /api/v1/reviews/{id}` (`ROLE_CUSTOMER`)

- [ ] **Step 1: Create DTOs with Validation annotations**
  `ReviewCreateRequest` (`orderItemId` not null, `rating` between 1 and 5, `comment` max 2000 chars, `imageUrls` max 5).
  `ReviewUpdateRequest` (`rating` between 1 and 5, `comment` max 2000 chars, `imageUrls` max 5).
- [ ] **Step 2: Write unit tests for `ReviewServiceImpl`**
  Cover:
  - Caller does not own order item $\to$ throws `BadRequestException("NOT_VERIFIED_PURCHASE")`.
  - Order status is not `DELIVERED` $\to$ throws `BadRequestException("ORDER_NOT_DELIVERED")`.
  - Duplicate review for item $\to$ throws `ConflictException("REVIEW_ALREADY_EXISTS")`.
  - Valid review creates `Review` and associated `ReviewImage` items.
  - Editing within 30 days succeeds and sets `is_edited = true`.
  - Editing after 30 days throws `BadRequestException("REVIEW_EDIT_WINDOW_EXPIRED")`.
- [ ] **Step 3: Implement `createReview` and `updateReview` in `ReviewServiceImpl`**
- [ ] **Step 4: Implement endpoints in `ReviewController`**
- [ ] **Step 5: Run integration tests**
  Run: `./mvnw test -Dtest=ReviewSubmissionIntegrationTest,ReviewServiceImplTest`
  Expected: PASS
- [ ] **Step 6: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/review/ src/test/java/com/aitechmarketplace/backend/review/
  git commit -m "feat(review): implement customer review submission and 30-day editing with verified purchase check"
  ```

---

### Task 4: Public Review Listing & Rating Summary Aggregation

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/review/dto/ReviewSummaryResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/repository/RatingDistributionProjection.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/review/repository/ReviewRepository.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/review/service/ReviewService.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/review/service/ReviewServiceImpl.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/review/controller/ReviewController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/review/controller/PublicReviewControllerIntegrationTest.java`

**Interfaces:**
- Produces: `ReviewSummaryResponse`:
  - `productId`, `averageRating` (double, 1 decimal place), `totalReviews` (long), `reviewsWithImagesCount` (long), `ratingDistribution` (Map<Integer, Long> for stars 1 to 5).
- Produces Endpoints:
  - `GET /api/v1/products/{productId}/reviews` (Public: supports `page`, `size`, `rating`, `hasImages`, `sort`: `newest`, `highest_rating`, `lowest_rating`, `most_helpful`)
  - `GET /api/v1/products/{productId}/reviews/summary` (Public)

- [ ] **Step 1: Write integration tests for public review listing and summary**
  Assert filtering by rating, filtering by `hasImages`, sort by `most_helpful`, and calculation of `averageRating` and distribution map.
- [ ] **Step 2: Add custom JPQL/native aggregation queries to `ReviewRepository`**
  Query for average rating, counts grouped by rating, and count where `images IS NOT EMPTY`.
- [ ] **Step 3: Implement listing and summary in `ReviewServiceImpl`**
  Exclude any review with `status = HIDDEN`.
- [ ] **Step 4: Expose public endpoints in `ReviewController`**
- [ ] **Step 5: Run integration tests**
  Run: `./mvnw test -Dtest=PublicReviewControllerIntegrationTest`
  Expected: PASS
- [ ] **Step 6: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/review/ src/test/java/com/aitechmarketplace/backend/review/
  git commit -m "feat(review): implement public review filtering, pagination, and rating metric summary"
  ```

---

### Task 5: Helpful Voting & Seller Reply Module

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/review/dto/HelpfulVoteResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/dto/SellerReplyRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/dto/SellerReplyResponse.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/review/service/ReviewService.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/review/service/ReviewServiceImpl.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/review/controller/ReviewController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/review/service/HelpfulVoteAndSellerReplyTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/review/controller/HelpfulVoteAndSellerReplyIntegrationTest.java`

**Interfaces:**
- Produces: `ReviewService`:
  - `HelpfulVoteResponse toggleHelpfulVote(Long userId, Long reviewId)`
  - `SellerReplyResponse postSellerReply(Long userId, boolean isAdmin, Long reviewId, SellerReplyRequest request)`
- Produces Endpoints:
  - `POST /api/v1/reviews/{id}/helpful` (`isAuthenticated()`)
  - `POST /api/v1/reviews/{id}/seller-reply` (`ROLE_SELLER` or `ROLE_ADMIN`)

- [ ] **Step 1: Write unit tests for helpful toggle and seller reply**
  - Self-voting throws `BadRequestException("CANNOT_VOTE_OWN_REVIEW")`.
  - First vote increments `helpful_count` and returns `voted: true`.
  - Second vote deletes vote record, decrements `helpful_count`, and returns `voted: false`.
  - Seller replying to their own product's review succeeds.
  - Seller replying to a review of another seller's product throws `ForbiddenException("FORBIDDEN_SELLER_REPLY")`.
  - Seller replying when a reply already exists throws `ConflictException("SELLER_ALREADY_REPLIED")`.
- [ ] **Step 2: Implement `toggleHelpfulVote` and `postSellerReply` in `ReviewServiceImpl`**
- [ ] **Step 3: Expose endpoints in `ReviewController`**
- [ ] **Step 4: Run integration tests**
  Run: `./mvnw test -Dtest=HelpfulVoteAndSellerReplyIntegrationTest,HelpfulVoteAndSellerReplyTest`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/review/ src/test/java/com/aitechmarketplace/backend/review/
  git commit -m "feat(review): implement helpful vote toggle and seller official reply APIs"
  ```

---

### Task 6: Administrative Review Moderation Module

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/review/dto/AdminReviewModerationRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/dto/AdminReviewModerationResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/review/controller/AdminReviewController.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/review/service/ReviewService.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/review/service/ReviewServiceImpl.java`
- Test: `src/test/java/com/aitechmarketplace/backend/review/controller/AdminReviewControllerIntegrationTest.java`

**Interfaces:**
- Produces: `ReviewService`:
  - `AdminReviewModerationResponse moderateReview(Long adminId, Long reviewId, AdminReviewModerationRequest request)`
- Produces Endpoint:
  - `PATCH /api/v1/admin/reviews/{id}/status` (`ROLE_ADMIN`)

- [ ] **Step 1: Write integration test for admin moderation**
  - Setting status to `HIDDEN` without reason fails validation.
  - Setting status to `HIDDEN` with reason marks review `HIDDEN`.
  - Non-admin user gets HTTP 403 Forbidden.
  - Once review is `HIDDEN`, public `GET /products/{id}/reviews` no longer returns it.
- [ ] **Step 2: Implement moderation method in `ReviewServiceImpl`**
- [ ] **Step 3: Create `AdminReviewController` with `@PreAuthorize("hasRole('ADMIN')")`**
- [ ] **Step 4: Run integration tests**
  Run: `./mvnw test -Dtest=AdminReviewControllerIntegrationTest`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/review/ src/test/java/com/aitechmarketplace/backend/review/
  git commit -m "feat(review): implement admin review moderation API"
  ```

---

### Task 7: Full Test Suite Verification & Documentation Alignment

**Files:**
- Modify: `README.md` (Update backend features list & API route summary)
- Test: Run full test suite across the entire application

- [ ] **Step 1: Run full Maven test suite**
  Run: `./mvnw clean test`
  Expected: BUILD SUCCESS with 100% test pass rate across all modules.
- [ ] **Step 2: Update backend README**
  Document `V11` migration and new endpoints under `/api/v1/products/{productId}/reviews`, `/api/v1/reviews/*`, and `/api/v1/admin/reviews/*`.
- [ ] **Step 3: Commit and verify clean working tree**
  ```bash
  git add README.md docs/superpowers/plans/2026-10-19-product-reviews-and-ratings-implementation.md
  git commit -m "docs: document product reviews and ratings in backend README and implementation plan"
  ```
