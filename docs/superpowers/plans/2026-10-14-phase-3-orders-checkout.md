# Phase 3: Orders, Checkout & Promotion System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Phase 3 core transactional capabilities in `ai-tech-marketplace-backend`: Customer Shipping Address management, Promotion & Coupon verification, Transactional Checkout pipeline with inventory reservation, Cart snapshotting, and Order Lifecycle management with cancellation rollbacks.

**Architecture:** Layered Spring Boot 3.4 architecture with Spring Data JPA transactions (`@Transactional`), optimistic/concurrency locking on stock, Flyway schema migrations (V7–V9), standardized `ApiResponse<T>` and `ErrorResponse` envelopes, and authoritative server-side financial calculations.

**Tech Stack:** Java 21, Spring Boot 3.4.3, Spring Data JPA, Spring Security, PostgreSQL, Flyway, JUnit 5, Mockito, MockMvc, AssertJ.

**Spec:**
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/api/addresses.md`
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/api/orders.md`
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/api/coupons.md`
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/database/schema.md`
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/requirements/business-rules.md`

## Global Constraints

- All REST endpoints must use the prefix `/api/v1/...` (e.g., `/api/v1/addresses`, `/api/v1/coupons`, `/api/v1/orders`).
- All success responses must wrap data in `ApiResponse<T>` (`success: true`, `data: T`, `message: String`).
- All error responses must use `ErrorResponse` (`success: false`, `data: null`, `message: String`, `errorCode: String`).
- The client MUST NOT supply financial figures (subtotal, total, discounts) during checkout; all calculations are strictly authoritative server-side.
- Address book limit: max 10 active addresses per user. If a user creates their first address, it must automatically become default.
- Order creation must be an atomic transaction (`@Transactional`): snapshot cart items, reserve inventory, record coupon usage, clear customer cart.
- Order cancellation (`POST /api/v1/orders/{id}/cancel`) must roll back reserved product stock and coupon quota if previously applied.
- All tests must pass via `./mvnw clean test`.

## Review Focus

1. **Checkout Concurrency & Overselling Protection**: Two simultaneous checkout requests for the last available item in stock must ensure only one succeeds and the other fails cleanly with HTTP 400 `OUT_OF_STOCK` without corrupting stock quantity.
2. **Authoritative Financial Calculations**: Tampered client inputs (or missing prices) are ignored; subtotal, shipping fee, coupon discount (percentage vs shipping vs fixed with caps), and total amount are computed strictly from database records.
3. **Immutable Snapshot Integrity**: Once an order is created, changes to product names, prices, categories, seller info, or user addresses must have zero effect on historical orders or order items.
4. **Coupon Quota & Eligibility Bounds**: Expired, inactive, subtotal-below-minimum, or usage-exhausted coupons must be rejected with exact error codes (`COUPON_EXPIRED`, `COUPON_MIN_ORDER_NOT_MET`, `COUPON_USER_LIMIT_EXCEEDED`).
5. **Address Ownership Isolation**: A user cannot checkout using another user's `addressId` (must throw HTTP 403 `FORBIDDEN` or 404 `ADDRESS_NOT_FOUND`).

---

### Task 1: Customer Shipping Addresses Module

**Files:**
- Create: `src/main/resources/db/migration/V7__create_addresses.sql`
- Create: `src/main/java/com/aitechmarketplace/backend/address/entity/Address.java`
- Create: `src/main/java/com/aitechmarketplace/backend/address/repository/AddressRepository.java`
- Create: `src/main/java/com/aitechmarketplace/backend/address/dto/AddressCreateRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/address/dto/AddressUpdateRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/address/dto/AddressResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/address/service/AddressService.java`
- Create: `src/main/java/com/aitechmarketplace/backend/address/service/AddressServiceImpl.java`
- Create: `src/main/java/com/aitechmarketplace/backend/address/controller/AddressController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/address/service/AddressServiceImplTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/address/controller/AddressControllerIntegrationTest.java`

**Interfaces:**
- Produces: `AddressService`:
  - `List<AddressResponse> getCustomerAddresses(Long userId)`
  - `AddressResponse createAddress(Long userId, AddressCreateRequest request)`
  - `AddressResponse updateAddress(Long userId, Long addressId, AddressUpdateRequest request)`
  - `void deleteAddress(Long userId, Long addressId)`
  - `AddressResponse setDefaultAddress(Long userId, Long addressId)`
- Produces Endpoints:
  - `GET /api/v1/addresses`
  - `POST /api/v1/addresses`
  - `PUT /api/v1/addresses/{id}`
  - `DELETE /api/v1/addresses/{id}`
  - `PATCH /api/v1/addresses/{id}/default`

- [ ] **Step 1: Write `V7__create_addresses.sql` migration**
  Table `addresses` with `id BIGSERIAL PRIMARY KEY`, `user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE`, `recipient_name VARCHAR(100) NOT NULL`, `phone VARCHAR(30) NOT NULL`, `address_line VARCHAR(255) NOT NULL`, `ward VARCHAR(100)`, `district VARCHAR(100)`, `city VARCHAR(100) NOT NULL`, `country VARCHAR(100) NOT NULL DEFAULT 'Vietnam'`, `is_default BOOLEAN NOT NULL DEFAULT FALSE`, `created_at`, `updated_at`. Index on `(user_id)`.
- [ ] **Step 2: Create `Address` entity and `AddressRepository`**
  Implement JPA entity with relationship to `User`, and repository methods `findByUserIdOrderByIdDesc(Long userId)`, `countByUserId(Long userId)`, `findByUserIdAndIsDefaultTrue(Long userId)`.
- [ ] **Step 3: Create Address DTOs with Jakarta validation**
  `AddressCreateRequest(recipientName, phone, addressLine, ward, district, city, country, isDefault)` with size/notblank validations.
- [ ] **Step 4: Write unit tests for AddressService**
  Test max 10 address limit check, first-address-auto-default logic, setting default address clearing previous default, ownership isolation.
- [ ] **Step 5: Implement `AddressService` & `AddressServiceImpl`**
  Implement business logic matching unit tests and `api/addresses.md`.
- [ ] **Step 6: Implement `AddressController`**
  Map `/api/v1/addresses` requiring authentication. Wrap responses in `ApiResponse`.
- [ ] **Step 7: Write and run integration tests**
  Test CRUD operations via MockMvc with JWT tokens.
  Run: `./mvnw test -Dtest=AddressControllerIntegrationTest,AddressServiceImplTest`
  Expected: PASS
- [ ] **Step 8: Commit**
  ```bash
  git add src/main/resources/db/migration/V7__create_addresses.sql src/main/java/com/aitechmarketplace/backend/address/ src/test/java/com/aitechmarketplace/backend/address/
  git commit -m "feat(address): implement customer shipping address management APIs"
  ```

---

### Task 2: Promotion & Coupon Database & Verification Module

**Files:**
- Create: `src/main/resources/db/migration/V8__create_coupons.sql`
- Create: `src/main/java/com/aitechmarketplace/backend/coupon/entity/Coupon.java`
- Create: `src/main/java/com/aitechmarketplace/backend/coupon/entity/CouponUsage.java`
- Create: `src/main/java/com/aitechmarketplace/backend/coupon/entity/DiscountType.java`
- Create: `src/main/java/com/aitechmarketplace/backend/coupon/repository/CouponRepository.java`
- Create: `src/main/java/com/aitechmarketplace/backend/coupon/repository/CouponUsageRepository.java`
- Create: `src/main/java/com/aitechmarketplace/backend/coupon/dto/CouponResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/coupon/dto/CouponValidateRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/coupon/dto/CouponValidateResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/coupon/dto/CouponCreateRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/coupon/service/CouponService.java`
- Create: `src/main/java/com/aitechmarketplace/backend/coupon/service/CouponServiceImpl.java`
- Create: `src/main/java/com/aitechmarketplace/backend/coupon/controller/CouponController.java`
- Create: `src/main/java/com/aitechmarketplace/backend/coupon/controller/AdminCouponController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/coupon/service/CouponServiceImplTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/coupon/controller/CouponControllerIntegrationTest.java`

**Interfaces:**
- Produces: `CouponService`:
  - `Page<CouponResponse> getPublicCoupons(Long userIdOrNull, Pageable pageable)`
  - `CouponValidateResponse validateCoupon(Long userId, String code, BigDecimal subtotal, BigDecimal shippingFee)`
  - `Coupon getValidCouponForOrder(String code, Long userId, BigDecimal subtotal, BigDecimal shippingFee)`
  - `void recordCouponUsage(Coupon coupon, Long userId, Long orderId, BigDecimal discountAmount)`
  - `void rollbackCouponUsage(Long orderId)`
- Produces Endpoints:
  - `GET /api/v1/coupons` (Public / Optional Auth)
  - `POST /api/v1/coupons/validate` (Authenticated)
  - `POST /api/v1/admin/coupons`, `GET /api/v1/admin/coupons` (Admin)

- [ ] **Step 1: Write `V8__create_coupons.sql` migration**
  Create `coupons` (`id`, `code VARCHAR(50) UNIQUE`, `name`, `description`, `discount_type VARCHAR(30)`, `discount_value NUMERIC(15,2)`, `max_discount_amount NUMERIC(15,2)`, `min_order_value NUMERIC(15,2)`, `usage_limit INT`, `used_count INT DEFAULT 0`, `usage_limit_per_user INT DEFAULT 1`, `is_public BOOLEAN DEFAULT TRUE`, `status VARCHAR(30) DEFAULT 'ACTIVE'`, `start_date TIMESTAMP WITH TIME ZONE`, `end_date TIMESTAMP WITH TIME ZONE`, timestamps).
  Create `coupon_usages` (`id`, `coupon_id FK`, `user_id FK`, `order_id BIGINT`, `discount_amount NUMERIC(15,2)`, `status VARCHAR(30)`, `used_at TIMESTAMP WITH TIME ZONE`, `released_at TIMESTAMP WITH TIME ZONE`).
- [ ] **Step 2: Create entities, enums (`DiscountType`: `PERCENTAGE`, `FIXED_AMOUNT`, `SHIPPING`), and repositories**
  Include `findByCode(String code)` and `countByCouponIdAndUserIdAndStatus(Long couponId, Long userId, String status)`.
- [ ] **Step 3: Write unit tests for discount calculation and eligibility rules**
  Test percentage discount calculation capped at `maxDiscountAmount`, shipping discount capped at shipping fee, min order subtotal checks, usage limits.
- [ ] **Step 4: Implement `CouponServiceImpl`**
  Implement validation logic with explicit error codes: `COUPON_NOT_FOUND`, `COUPON_EXPIRED`, `COUPON_INACTIVE`, `COUPON_USAGE_LIMIT_EXCEEDED`, `COUPON_USER_LIMIT_EXCEEDED`, `COUPON_MIN_ORDER_NOT_MET`.
- [ ] **Step 5: Implement `CouponController` & `AdminCouponController`**
  Expose customer validation and public listing, plus admin CRUD. Wrap with `ApiResponse`.
- [ ] **Step 6: Write and run integration tests**
  Run: `./mvnw test -Dtest=CouponControllerIntegrationTest,CouponServiceImplTest`
  Expected: PASS
- [ ] **Step 7: Commit**
  ```bash
  git add src/main/resources/db/migration/V8__create_coupons.sql src/main/java/com/aitechmarketplace/backend/coupon/ src/test/java/com/aitechmarketplace/backend/coupon/
  git commit -m "feat(coupon): implement promotions, voucher discovery, and pre-checkout validation"
  ```

---

### Task 3: Orders Database Schema & Core Entities

**Files:**
- Create: `src/main/resources/db/migration/V9__create_orders.sql`
- Create: `src/main/java/com/aitechmarketplace/backend/order/entity/Order.java`
- Create: `src/main/java/com/aitechmarketplace/backend/order/entity/OrderItem.java`
- Create: `src/main/java/com/aitechmarketplace/backend/order/entity/OrderStatus.java`
- Create: `src/main/java/com/aitechmarketplace/backend/order/entity/PaymentStatus.java`
- Create: `src/main/java/com/aitechmarketplace/backend/order/entity/PaymentMethod.java`
- Create: `src/main/java/com/aitechmarketplace/backend/order/entity/Payment.java`
- Create: `src/main/java/com/aitechmarketplace/backend/order/repository/OrderRepository.java`
- Create: `src/main/java/com/aitechmarketplace/backend/order/repository/OrderItemRepository.java`
- Create: `src/main/java/com/aitechmarketplace/backend/order/repository/PaymentRepository.java`
- Create: `src/main/java/com/aitechmarketplace/backend/order/dto/OrderCreateRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/order/dto/OrderResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/order/dto/OrderItemResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/order/dto/ShippingAddressDto.java`

**Interfaces:**
- Produces: Tables `orders`, `order_items`, `payments`.
- Produces: Enums:
  - `OrderStatus`: `PENDING`, `CONFIRMED`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`
  - `PaymentStatus`: `PENDING`, `PAID`, `FAILED`, `REFUNDED`
  - `PaymentMethod`: `COD`, `VNPAY`, `MOMO`, `BANK_TRANSFER`
- Produces DTO: `OrderCreateRequest(Long addressId, PaymentMethod paymentMethod, String couponCode)`

- [ ] **Step 1: Write `V9__create_orders.sql` migration**
  Create `orders` matching `schema.md` (order_number unique, user_id, status, payment_method, payment_status, subtotal, shipping_fee, discount_amount, total_amount, shipping_recipient_name, shipping_phone, shipping_address, created_at, updated_at).
  Create `order_items` (order_id FK, product_id FK, product_name, sku, quantity, unit_price, subtotal).
  Create `payments` (order_id FK, payment_method, transaction_reference, amount, status, timestamps).
- [ ] **Step 2: Create JPA entities and enums with proper relationship mappings**
  `Order` has `@OneToMany(mappedBy = "order", cascade = CascadeType.ALL) List<OrderItem> items`.
- [ ] **Step 3: Create JPA repositories**
  `OrderRepository` with `findByUserId(Long userId, Pageable pageable)`, `findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable)`, `findByOrderNumber(String orderNumber)`.
- [ ] **Step 4: Verify migration run**
  Verify schema compiles cleanly.
- [ ] **Step 5: Commit**
  ```bash
  git add src/main/resources/db/migration/V9__create_orders.sql src/main/java/com/aitechmarketplace/backend/order/
  git commit -m "feat(order): define order, order item, and payment entities with V9 migration"
  ```

---

### Task 4: Transactional Checkout Pipeline (`POST /api/v1/orders`)

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/order/service/OrderService.java`
- Create: `src/main/java/com/aitechmarketplace/backend/order/service/OrderServiceImpl.java`
- Create: `src/main/java/com/aitechmarketplace/backend/order/controller/OrderController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/order/service/OrderServiceImplTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/order/controller/OrderControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `AddressRepository`, `CartRepository`, `CartItemRepository`, `ProductRepository`, `CouponService`
- Produces: `POST /api/v1/orders` returning `ApiResponse<OrderResponse>` (HTTP 201 Created)

- [ ] **Step 1: Write unit tests for checkout pipeline**
  - Test successful order placement: cart items converted to order items, subtotal calculated, inventory stock deducted, coupon applied, cart cleared.
  - Test cart empty error (`CART_EMPTY`, HTTP 400).
  - Test address not found or not owned by user (`ADDRESS_NOT_FOUND` / `FORBIDDEN`).
  - Test product out of stock or insufficient quantity (`OUT_OF_STOCK`, HTTP 400).
  - Test inactive product in cart (`PRODUCT_NOT_AVAILABLE`, HTTP 400).
- [ ] **Step 2: Implement order creation in `OrderServiceImpl`**
  - Enforce `@Transactional`.
  - Fetch active Cart for `userId`. Throw `BadRequestException("Cart is empty", "CART_EMPTY")` if items empty.
  - Fetch `Address` by `addressId` and verify `address.getUser().getId().equals(userId)`.
  - Compute shipping fee (standard fixed fee e.g. 30,000 VND or 0 if free shipping threshold).
  - Loop cart items: re-verify product is active and `product.getStockQuantity() >= item.getQuantity()`. Deduct `product.setStockQuantity(product.getStockQuantity() - item.getQuantity())`.
  - If `couponCode` provided: invoke `couponService.validateCoupon(...)` and `couponService.recordCouponUsage(...)`.
  - Build `Order` with immutable snapshot of address and item fields.
  - Delete cart items via `cartItemRepository.deleteByCartId(cart.getId())`.
- [ ] **Step 3: Expose `POST /api/v1/orders` in `OrderController`**
  Map `@PostMapping` under `/api/v1/orders`, `@RequestAttribute("userId") Long userId`, `@Valid @RequestBody OrderCreateRequest`. Return HTTP 201 Created wrapped in `ApiResponse`.
- [ ] **Step 4: Run unit and integration tests**
  Run: `./mvnw test -Dtest=OrderControllerIntegrationTest#createOrder_shouldSucceed,OrderServiceImplTest`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/order/ src/test/java/com/aitechmarketplace/backend/order/
  git commit -m "feat(order): implement transactional checkout pipeline with stock reservation"
  ```

---

### Task 5: Order Query & Detail Retrieval

**Files:**
- Modify: `src/main/java/com/aitechmarketplace/backend/order/service/OrderService.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/order/service/OrderServiceImpl.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/order/controller/OrderController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/order/controller/OrderControllerIntegrationTest.java`

**Interfaces:**
- Produces: `GET /api/v1/orders` (User orders, paginated, optional status filter)
- Produces: `GET /api/v1/orders/{id}` (Order detail, user ownership or Admin check)

- [ ] **Step 1: Write test for order listing and detail retrieval**
  - Verify customer only sees their own orders.
  - Verify filtering by status (`status=PENDING`).
  - Verify 404 when order does not exist or belongs to another user.
- [ ] **Step 2: Implement query methods in `OrderServiceImpl`**
  Implement `getUserOrders(userId, status, pageable)` and `getOrderById(orderId, userId, isAdmin)`.
- [ ] **Step 3: Implement GET endpoints in `OrderController`**
  Map `GET /api/v1/orders` and `GET /api/v1/orders/{id}`.
- [ ] **Step 4: Run tests**
  Run: `./mvnw test -Dtest=OrderControllerIntegrationTest`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/order/ src/test/java/com/aitechmarketplace/backend/order/
  git commit -m "feat(order): implement order history listing and order detail retrieval"
  ```

---

### Task 6: Order Cancellation & Inventory/Coupon Rollback

**Files:**
- Modify: `src/main/java/com/aitechmarketplace/backend/order/service/OrderService.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/order/service/OrderServiceImpl.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/order/controller/OrderController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/order/service/OrderServiceImplTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/order/controller/OrderControllerIntegrationTest.java`

**Interfaces:**
- Produces: `POST /api/v1/orders/{id}/cancel` returning updated `ApiResponse<OrderResponse>`

- [ ] **Step 1: Write unit and integration tests for cancellation**
  - Test cancellation succeeds when status is `PENDING` or `CONFIRMED`.
  - Verify inventory `stockQuantity` is restored for all order items.
  - Verify applied coupon usage is rolled back (`released_at` set, `coupons.used_count` decremented).
  - Test cancellation fails when status is `SHIPPED` or `DELIVERED` (`ORDER_CANNOT_BE_CANCELLED`, HTTP 400).
- [ ] **Step 2: Implement `cancelOrder` in `OrderServiceImpl`**
  - Enforce `@Transactional`.
  - Validate state: only `PENDING` or `CONFIRMED` can be cancelled.
  - Iterate `order.getItems()`: add back `item.getQuantity()` to `product.stockQuantity`.
  - Invoke `couponService.rollbackCouponUsage(order.getId())`.
  - Update `order.setStatus(OrderStatus.CANCELLED)`.
- [ ] **Step 3: Expose `POST /api/v1/orders/{id}/cancel` in `OrderController`**
  Validate ownership or admin authority.
- [ ] **Step 4: Run cancellation tests**
  Run: `./mvnw test -Dtest=OrderControllerIntegrationTest#cancelOrder_shouldRestoreStockAndCoupon`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/order/ src/test/java/com/aitechmarketplace/backend/order/
  git commit -m "feat(order): implement order cancellation with stock and coupon rollback"
  ```

---

### Task 7: Full Test Suite Verification & Roadmap Synchronization

**Files:**
- Modify: `ai-tech-marketplace-docs/development/roadmap.md`

- [ ] **Step 1: Run complete maven test suite**
  Run: `./mvnw clean test`
  Verify: 100% test pass rate across all modules including new Address, Coupon, and Order suites.
- [ ] **Step 2: Verify database migrations on clean schema**
  Verify Flyway runs V1 through V9 cleanly.
- [ ] **Step 3: Update documentation roadmap**
  Synchronize `roadmap.md` Phase 3 deliverables to marked completed.
- [ ] **Step 4: Commit**
  ```bash
  git add ai-tech-marketplace-docs/development/roadmap.md
  git commit -m "docs(roadmap): mark Phase 3 orders, checkout, addresses and coupons as completed"
  ```
