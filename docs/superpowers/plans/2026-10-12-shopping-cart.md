# Shopping Cart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Shopping Cart functionality for AI Tech Marketplace backend adhering to `/api/v1/cart` API contracts and business rules.

**Architecture:** Spring Boot layered architecture (Flyway, Entity, Repository, Service, Controller, DTOs, Exception handling) backed by PostgreSQL. Stateless JWT authentication provides `userId` from request attributes.

**Tech Stack:** Java 21, Spring Boot 3.4.3, Spring Data JPA, Spring Security, Flyway, PostgreSQL, JUnit 5, MockMvc, AssertJ.

**Spec:**
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/api/cart.md`
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/requirements/business-rules.md` (BR-CART-001, BR-CART-002, BR-CART-003)
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/database/schema.md` (Sections 13 & 14)

## Global Constraints

- Database migration version: `V6__create_carts.sql` in `src/main/resources/db/migration/`
- All response envelopes match `/api/v1/cart` contract format: `ApiResponse<T>` with fields `success` (boolean), `data` (T), and `message` (String)
- Endpoints support both `/api/v1/cart` (official contract) and `/api/cart` (codebase consistency)
- Positive quantity rule: Cart item quantity must be greater than zero (`quantity > 0`) (BR-CART-001)
- Availability rule: Products must exist, be active, and total quantity in cart must not exceed stock (BR-CART-002)
- Ownership rule: Users can only view and mutate their own cart (BR-CART-003)
- All new and existing tests must pass (`./mvnw test`)

## Review Focus

- **Existing item in cart:** Adding an existing product increments its quantity rather than creating a duplicate row; rejected if resulting quantity exceeds available stock.
- **Stock boundary check:** Attempting to set or add quantity exceeding product `stock_quantity` throws `BadRequestException` ("Quantity exceeds available stock").
- **Inactive or missing product:** Adding an inactive or nonexistent product throws `BadRequestException` ("Product is not available") or `NotFoundException` ("Product not found").
- **Cart item ownership:** Updating or deleting a cart item ID belonging to another user throws `NotFoundException` ("Cart item not found").
- **Fresh cart creation:** When a user with no existing cart requests `GET /api/v1/cart`, return an empty cart response (`items: []`, `totalItems: 0`, `totalAmount: 0.00`).

---

### Task 1: Flyway Migration `V6__create_carts.sql`

**Files:**
- Create: `src/main/resources/db/migration/V6__create_carts.sql`

**Interfaces:**
- Produces: Tables `carts` and `cart_items` with appropriate foreign keys, constraints, and indexes.

- [ ] **Step 1: Create Flyway migration file `V6__create_carts.sql`**
  Define `carts` table:
  - `id BIGSERIAL PRIMARY KEY`
  - `user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE`
  - `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `CONSTRAINT uk_carts_user_id UNIQUE (user_id)`
  Define `cart_items` table:
  - `id BIGSERIAL PRIMARY KEY`
  - `cart_id BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE`
  - `product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE`
  - `quantity INTEGER NOT NULL`
  - `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `CONSTRAINT uk_cart_items_cart_product UNIQUE (cart_id, product_id)`
  - `CONSTRAINT chk_cart_items_quantity CHECK (quantity > 0)`
  - `CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id)`
  - `CREATE INDEX idx_cart_items_product_id ON cart_items(product_id)`

- [ ] **Step 2: Verify migration runs with existing tests**
  Run: `./mvnw test-compile`
  Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**
  ```bash
  git add src/main/resources/db/migration/V6__create_carts.sql
  git commit -m "feat(cart): add V6 flyway migration for carts and cart_items"
  ```

---

### Task 2: Standard `ApiResponse` Envelope

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/common/dto/ApiResponse.java`
- Test: `src/test/java/com/aitechmarketplace/backend/common/dto/ApiResponseTest.java`

**Interfaces:**
- Produces: `ApiResponse<T>(boolean success, T data, String message)` with static convenience factories `success(T data)` and `success(T data, String message)`.

- [ ] **Step 1: Write unit test for `ApiResponse`**
  ```java
  @Test
  void shouldCreateSuccessfulResponse() {
      var response = ApiResponse.success("test", "Created");
      assertThat(response.success()).isTrue();
      assertThat(response.data()).isEqualTo("test");
      assertThat(response.message()).isEqualTo("Created");
  }
  ```

- [ ] **Step 2: Run test to verify it fails**
  Run: `./mvnw test -Dtest=ApiResponseTest`
  Expected: Compilation failure / Class not found

- [ ] **Step 3: Implement `ApiResponse<T>` record**
  In `src/main/java/com/aitechmarketplace/backend/common/dto/ApiResponse.java`:
  ```java
  package com.aitechmarketplace.backend.common.dto;

  public record ApiResponse<T>(
      boolean success,
      T data,
      String message
  ) {
      public static <T> ApiResponse<T> success(T data) {
          return new ApiResponse<>(true, data, null);
      }

      public static <T> ApiResponse<T> success(T data, String message) {
          return new ApiResponse<>(true, data, message);
      }
  }
  ```

- [ ] **Step 4: Run test to verify it passes**
  Run: `./mvnw test -Dtest=ApiResponseTest`
  Expected: PASS

- [ ] **Step 5: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/common/dto/ApiResponse.java src/test/java/com/aitechmarketplace/backend/common/dto/ApiResponseTest.java
  git commit -m "feat(common): add ApiResponse envelope DTO"
  ```

---

### Task 3: Cart Entities and Repositories

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/cart/entity/Cart.java`
- Create: `src/main/java/com/aitechmarketplace/backend/cart/entity/CartItem.java`
- Create: `src/main/java/com/aitechmarketplace/backend/cart/repository/CartRepository.java`
- Create: `src/main/java/com/aitechmarketplace/backend/cart/repository/CartItemRepository.java`

**Interfaces:**
- Produces:
  - `Cart` JPA entity mapping `carts` table with relationship to `User` and one-to-many to `CartItem`.
  - `CartItem` JPA entity mapping `cart_items` table with relationship to `Cart` and `Product`.
  - `CartRepository.findByUserId(Long userId)`
  - `CartItemRepository.findByIdAndCartId(Long id, Long cartId)`
  - `CartItemRepository.findByCartIdAndProductId(Long cartId, Long productId)`
  - `CartItemRepository.deleteByCartId(Long cartId)`

- [ ] **Step 1: Create `Cart` entity**
  `Cart` has `id`, `user` (`@OneToOne` or `@ManyToOne(fetch = LAZY) @JoinColumn(name = "user_id")`), `items` (`@OneToMany(mappedBy = "cart", cascade = ALL, orphanRemoval = true)`), `createdAt`, `updatedAt` (`@PrePersist`, `@PreUpdate`).

- [ ] **Step 2: Create `CartItem` entity**
  `CartItem` has `id`, `cart` (`@ManyToOne(fetch = LAZY) @JoinColumn(name = "cart_id")`), `product` (`@ManyToOne(fetch = LAZY) @JoinColumn(name = "product_id")`), `quantity`, `createdAt`, `updatedAt` (`@PrePersist`, `@PreUpdate`).

- [ ] **Step 3: Create `CartRepository` interface**
  Extends `JpaRepository<Cart, Long>`:
  - `Optional<Cart> findByUserId(Long userId);`

- [ ] **Step 4: Create `CartItemRepository` interface**
  Extends `JpaRepository<CartItem, Long>`:
  - `Optional<CartItem> findByIdAndCartId(Long id, Long cartId);`
  - `Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);`
  - `List<CartItem> findByCartIdOrderByIdAsc(Long cartId);`
  - `void deleteByCartId(Long cartId);`

- [ ] **Step 5: Verify entity mappings compile and pass schema validation**
  Run: `./mvnw test-compile`
  Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/cart/
  git commit -m "feat(cart): add Cart and CartItem entities and repositories"
  ```

---

### Task 4: Cart DTOs

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/cart/dto/CartProductResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/cart/dto/CartItemResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/cart/dto/CartResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/cart/dto/CartItemAddRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/cart/dto/CartItemUpdateRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/cart/dto/CartItemActionResponse.java`

**Interfaces:**
- Produces:
  - `CartProductResponse(Long id, String name, BigDecimal price, String thumbnailUrl)`
  - `CartItemResponse(Long id, CartProductResponse product, Integer quantity, BigDecimal subtotal)`
  - `CartResponse(Long id, List<CartItemResponse> items, Integer totalItems, BigDecimal totalAmount)`
  - `CartItemAddRequest(@NotNull Long productId, @NotNull @Min(1) Integer quantity)`
  - `CartItemUpdateRequest(@NotNull @Min(1) Integer quantity)`
  - `CartItemActionResponse(Long id, Long productId, Integer quantity)`

- [ ] **Step 1: Create request DTOs with validation annotations**
  - `CartItemAddRequest(Long productId, Integer quantity)` with `@NotNull`, `@Min(1)`.
  - `CartItemUpdateRequest(Integer quantity)` with `@NotNull`, `@Min(1)`.

- [ ] **Step 2: Create response DTOs matching `cart.md` specification**
  - `CartProductResponse`: includes product `id`, `name`, `price`, `thumbnailUrl`.
  - `CartItemResponse`: includes `id`, `product`, `quantity`, `subtotal` (`price * quantity`).
  - `CartResponse`: includes `id`, `items`, `totalItems` (sum of quantities), `totalAmount` (sum of subtotals).
  - `CartItemActionResponse`: includes `id` (cart item id), `productId`, `quantity`.

- [ ] **Step 3: Compile check**
  Run: `./mvnw test-compile`
  Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/cart/dto/
  git commit -m "feat(cart): add cart request and response DTOs"
  ```

---

### Task 5: Cart Service and Unit Tests

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/cart/service/CartService.java`
- Create: `src/main/java/com/aitechmarketplace/backend/cart/service/CartServiceImpl.java`
- Create: `src/test/java/com/aitechmarketplace/backend/cart/service/CartServiceImplTest.java`

**Interfaces:**
- Produces: `CartService` interface and `CartServiceImpl` implementing:
  - `CartResponse getCart(Long userId)`
  - `CartItemActionResponse addItem(Long userId, Long productId, Integer quantity)`
  - `CartItemActionResponse updateItem(Long userId, Long cartItemId, Integer quantity)`
  - `void removeItem(Long userId, Long cartItemId)`
  - `void clearCart(Long userId)`

- [ ] **Step 1: Write failing unit tests for `CartServiceImpl`**
  Cover in `CartServiceImplTest`:
  - `getCart_shouldReturnEmptyCart_whenNoCartExists`
  - `getCart_shouldCalculateSubtotalAndTotalAmount`
  - `addItem_shouldCreateCartAndAddItem_whenProductValid`
  - `addItem_shouldIncrementQuantity_whenItemAlreadyExists`
  - `addItem_shouldThrowNotFoundException_whenProductDoesNotExist`
  - `addItem_shouldThrowBadRequestException_whenProductInactive`
  - `addItem_shouldThrowBadRequestException_whenQuantityExceedsStock`
  - `updateItem_shouldUpdateQuantity_whenValid`
  - `updateItem_shouldThrowNotFoundException_whenItemNotFoundOrNotOwned`
  - `updateItem_shouldThrowBadRequestException_whenQuantityExceedsStock`
  - `removeItem_shouldRemoveItem_whenOwned`
  - `removeItem_shouldThrowNotFoundException_whenNotOwned`
  - `clearCart_shouldRemoveAllItems`

- [ ] **Step 2: Run test to verify it fails**
  Run: `./mvnw test -Dtest=CartServiceImplTest`
  Expected: Compilation failure or FAIL

- [ ] **Step 3: Implement `CartService` and `CartServiceImpl`**
  - Dependency injection: `CartRepository`, `CartItemRepository`, `ProductRepository`, `UserRepository`, `ProductImageRepository`.
  - Helper method `resolveThumbnailUrl(Long productId)`: queries `ProductImageRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId)` and returns `/api/products/{productId}/images/{id}/content` for the first image, or `null`.
  - Helper method `getOrCreateCart(Long userId)`: finds existing `Cart` or builds a new one and saves it.
  - Implement validation and exception handling with `NotFoundException` and `BadRequestException`.

- [ ] **Step 4: Run unit tests to verify they pass**
  Run: `./mvnw test -Dtest=CartServiceImplTest`
  Expected: All tests PASS

- [ ] **Step 5: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/cart/service/ src/test/java/com/aitechmarketplace/backend/cart/service/
  git commit -m "feat(cart): implement CartService with business rules and unit tests"
  ```

---

### Task 6: Cart Controller and Integration Tests

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/cart/controller/CartController.java`
- Create: `src/test/java/com/aitechmarketplace/backend/cart/controller/CartControllerIntegrationTest.java`

**Interfaces:**
- Produces: REST endpoints matching `/api/v1/cart` and `/api/cart`:
  - `GET /api/v1/cart` & `GET /api/cart` -> `ApiResponse<CartResponse>` (Status 200)
  - `POST /api/v1/cart/items` & `POST /api/cart/items` -> `ApiResponse<CartItemActionResponse>` (Status 200)
  - `PUT /api/v1/cart/items/{itemId}` & `PUT /api/cart/items/{itemId}` -> `ApiResponse<CartItemActionResponse>` (Status 200)
  - `DELETE /api/v1/cart/items/{itemId}` & `DELETE /api/cart/items/{itemId}` -> Status 204 No Content
  - `DELETE /api/v1/cart/items` & `DELETE /api/cart/items` -> Status 204 No Content

- [ ] **Step 1: Write integration tests for `CartController`**
  In `CartControllerIntegrationTest` using `@SpringBootTest`, `@AutoConfigureMockMvc`:
  - `getCart_shouldRequireAuthentication` (401 when no token)
  - `getCart_shouldReturnEmptyCart_whenNewUser`
  - `addCartItem_shouldAddProductAndReturn200`
  - `addCartItem_shouldIncrementQuantity_whenProductAlreadyInCart`
  - `addCartItem_shouldReturn400_whenQuantityZeroOrNegative`
  - `addCartItem_shouldReturn400_whenStockExceeded`
  - `updateCartItem_shouldUpdateQuantityAndReturn200`
  - `updateCartItem_shouldReturn404_whenItemNotOwned`
  - `deleteCartItem_shouldReturn204`
  - `clearCart_shouldRemoveAllItemsAndReturn204`

- [ ] **Step 2: Run test to verify it fails**
  Run: `./mvnw test -Dtest=CartControllerIntegrationTest`
  Expected: FAIL (endpoint not mapped / 404)

- [ ] **Step 3: Implement `CartController`**
  ```java
  package com.aitechmarketplace.backend.cart.controller;

  @RestController
  @RequestMapping({"/api/v1/cart", "/api/cart"})
  public class CartController {
      private final CartService cartService;
      ...
  }
  ```
  Inject `@RequestAttribute("userId") Long userId` on each endpoint.
  Return `ApiResponse.success(...)` for GET, POST, PUT.
  Return `@ResponseStatus(HttpStatus.NO_CONTENT)` for DELETE.

- [ ] **Step 4: Run integration tests to verify they pass**
  Run: `./mvnw test -Dtest=CartControllerIntegrationTest`
  Expected: All integration tests PASS

- [ ] **Step 5: Run full test suite**
  Run: `./mvnw test`
  Expected: BUILD SUCCESS (all existing + new tests pass)

- [ ] **Step 6: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/cart/controller/ src/test/java/com/aitechmarketplace/backend/cart/controller/
  git commit -m "feat(cart): implement CartController and integration tests"
  ```
