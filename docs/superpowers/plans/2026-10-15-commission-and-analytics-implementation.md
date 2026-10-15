# Commission Management & Multi-Tier Financial Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement marketplace commission configuration with hierarchical precedence (`Seller` $\to$ `Category` $\to$ `Global`), transactional settlement snapshotting (`seller_order_settlements`) during checkout and order cancellation, and multi-tier financial analytics APIs for Platform Administrators, Merchants/Sellers, and Customers.

**Architecture:** Layered Spring Boot 4 architecture with Spring Data JPA transactions (`@Transactional`), Flyway schema migration (`V10`), immutable historical snapshotting at order placement, authoritative server-side metric aggregations, and strict multi-tenant data isolation via Spring Security role checks.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Data JPA, Hibernate, PostgreSQL 17, Flyway, Spring Security 6, JUnit 5, Mockito, MockMvc, AssertJ.

**Spec:**
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/docs/superpowers/specs/2026-10-15-commission-and-analytics-design.md`
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/api/commission.md`
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/api/analytics.md`
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/database/schema.md` (Sections 23, 24)
- `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/requirements/business-rules.md` (BR-COMMISSION-*, BR-ANALYTICS-*)

---

## Global Constraints

- All REST endpoints must use the prefix `/api/v1/...` (e.g., `/api/v1/admin/commission-settings`, `/api/v1/admin/analytics`, `/api/v1/seller/analytics`, `/api/v1/customer/analytics`).
- All success responses must wrap data in `ApiResponse<T>` (`success: true`, `data: T`, `error: null`, `timestamp: String`).
- All error responses must use `ErrorResponse` (`code: String`, `message: String`, `details: Object`, `timestamp: String`) handled via `GlobalExceptionHandler`.
- Commission rates must satisfy $0.0000 \le \text{rate} \le 1.0000$ (0% to 100%).
- Commission rate resolution follows strict precedence:
  $$\text{Level 1: Seller-Specific} \longrightarrow \text{Level 2: Category-Specific} \longrightarrow \text{Level 3: Global Platform Default} \longrightarrow \text{Fallback: 5.0% (0.0500)}$$
- Item settlements must be snapshot atomically inside the checkout transaction (`seller_order_settlements`). Historical settlement calculations are immutable to future rate changes.
- Cancellation of an order before delivery must transition associated settlements to `CANCELLED`.
- Data isolation is mandatory:
  - `ROLE_ADMIN`: Platform-wide visibility.
  - `ROLE_SELLER`: Strictly isolated to the authenticated seller's store data (`seller_id = currentUser.getId()`).
  - `ROLE_CUSTOMER`: Strictly isolated to the authenticated customer's own orders (`user_id = currentUser.getId()`).
- All tests must pass via `./mvnw test`.

---

## Review Focus

1. **Precedence Hierarchy Accuracy**: When an active seller-specific rate exists, it must override category and global rates; when only category exists, it must override global; when none exists, it must cleanly fall back to global or hardcoded 5%.
2. **Immutable Snapshot Integrity**: Changing a commission setting or category rate must never alter previously recorded records in `seller_order_settlements`.
3. **Escrow Lifecycle & Cancellation Rollback**: Placing an order sets settlement status to `PENDING`; cancelling the order transitions it to `CANCELLED` and does not count towards seller net earnings or platform net revenue.
4. **Time Range & Interval Bounds**: Date filtering defaults to the last 30 calendar days if omitted; ranges exceeding 365 days must fail validation cleanly.
5. **Multi-Tenant Access Isolation**: A seller attempting to view another seller's analytics, or a customer querying another user's spending, must return HTTP 403 Forbidden or empty datasets.

---

### Task 1: Flyway Migration V10 & JPA Entities for Commission and Settlements

**Files:**
- Create: `src/main/resources/db/migration/V10__create_commission_and_settlements.sql`
- Create: `src/main/java/com/aitechmarketplace/backend/commission/entity/CommissionScope.java`
- Create: `src/main/java/com/aitechmarketplace/backend/commission/entity/CommissionSetting.java`
- Create: `src/main/java/com/aitechmarketplace/backend/commission/repository/CommissionSettingRepository.java`
- Create: `src/main/java/com/aitechmarketplace/backend/settlement/entity/SettlementStatus.java`
- Create: `src/main/java/com/aitechmarketplace/backend/settlement/entity/SellerOrderSettlement.java`
- Create: `src/main/java/com/aitechmarketplace/backend/settlement/repository/SellerOrderSettlementRepository.java`
- Test: `src/test/java/com/aitechmarketplace/backend/commission/repository/CommissionSettingRepositoryTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/settlement/repository/SellerOrderSettlementRepositoryTest.java`

**Interfaces:**
- Produces: `CommissionScope` enum: `GLOBAL`, `CATEGORY`, `SELLER`.
- Produces: `SettlementStatus` enum: `PENDING`, `SETTLED`, `CANCELLED`.
- Produces: `CommissionSettingRepository`:
  - `Optional<CommissionSetting> findByScopeAndTargetId(CommissionScope scope, Long targetId)`
  - `Optional<CommissionSetting> findByScopeAndTargetIdAndIsActiveTrue(CommissionScope scope, Long targetId)`
  - `Optional<CommissionSetting> findByScopeAndIsActiveTrue(CommissionScope scope)`
  - `List<CommissionSetting> findAllByIsActive(Boolean isActive)`
  - `boolean existsByScopeAndTargetId(CommissionScope scope, Long targetId)`
- Produces: `SellerOrderSettlementRepository`:
  - `List<SellerOrderSettlement> findByOrderId(Long orderId)`
  - `List<SellerOrderSettlement> findBySellerId(Long sellerId)`
  - `Page<SellerOrderSettlement> findBySellerIdAndStatus(Long sellerId, SettlementStatus status, Pageable pageable)`

- [ ] **Step 1: Write `V10__create_commission_and_settlements.sql` migration**
  Create `commission_settings` and `seller_order_settlements` tables with primary keys, foreign keys (`orders(id)`, `order_items(id)`, `users(id)`), checks (`rate >= 0.0000 AND rate <= 1.0000`), unique constraint `(scope, target_id)`, and performance indexes matching `database/schema.md`.
- [ ] **Step 2: Create Enums and Entities**
  Implement `CommissionScope`, `SettlementStatus`, `CommissionSetting` entity, and `SellerOrderSettlement` entity with JPA annotations and audit fields.
- [ ] **Step 3: Create Spring Data Repositories**
  Define repository interfaces with lookup and existence methods.
- [ ] **Step 4: Write repository tests**
  Test unique constraint enforcement on `(scope, target_id)`, rate boundary checks, and foreign key cascade behaviors.
  Run: `./mvnw test -Dtest=CommissionSettingRepositoryTest,SellerOrderSettlementRepositoryTest`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add src/main/resources/db/migration/V10__create_commission_and_settlements.sql src/main/java/com/aitechmarketplace/backend/commission/ src/main/java/com/aitechmarketplace/backend/settlement/ src/test/java/com/aitechmarketplace/backend/
  git commit -m "feat(db): add V10 migration and entities for commission settings and settlements"
  ```

---

### Task 2: Commission Management Administration Module

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/commission/dto/CommissionSettingCreateRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/commission/dto/CommissionSettingUpdateRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/commission/dto/CommissionSettingResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/commission/service/CommissionSettingService.java`
- Create: `src/main/java/com/aitechmarketplace/backend/commission/service/CommissionSettingServiceImpl.java`
- Create: `src/main/java/com/aitechmarketplace/backend/commission/controller/AdminCommissionController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/commission/service/CommissionSettingServiceImplTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/commission/controller/AdminCommissionControllerIntegrationTest.java`

**Interfaces:**
- Produces: `CommissionSettingService`:
  - `List<CommissionSettingResponse> findAll(CommissionScope scope, Boolean isActive)`
  - `CommissionSettingResponse findById(Long id)`
  - `CommissionSettingResponse create(CommissionSettingCreateRequest request)`
  - `CommissionSettingResponse update(Long id, CommissionSettingUpdateRequest request)`
  - `void delete(Long id)`
  - `BigDecimal resolveCommissionRate(Long sellerId, Long categoryId)`
- Produces Endpoints:
  - `GET /api/v1/admin/commission-settings`
  - `POST /api/v1/admin/commission-settings`
  - `PUT /api/v1/admin/commission-settings/{id}`
  - `DELETE /api/v1/admin/commission-settings/{id}`

- [ ] **Step 1: Create DTOs with Validation**
  Create `CommissionSettingCreateRequest(scope, targetId, rate, description, isActive)` and `CommissionSettingUpdateRequest(rate, description, isActive)` with Jakarta validation (`@DecimalMin("0.0000")`, `@DecimalMax("1.0000")`).
- [ ] **Step 2: Write unit test for `CommissionSettingService`**
  Cover precedence resolution:
  - When seller rate exists $\to$ returns seller rate.
  - When only category rate exists $\to$ returns category rate.
  - When only global rate exists $\to$ returns global rate.
  - When no rules exist $\to$ returns default `0.0500`.
  - Duplicate `(scope, target_id)` creation $\to$ throws `ConflictException("COMMISSION_DUPLICATE_SCOPE")`.
- [ ] **Step 3: Implement `CommissionSettingServiceImpl`**
  Implement CRUD operations and rate resolution logic according to `BR-COMMISSION-001` and `BR-COMMISSION-002`.
- [ ] **Step 4: Implement `AdminCommissionController`**
  Protect with `@PreAuthorize("hasRole('ADMIN')")`, returning `ApiResponse<T>`.
- [ ] **Step 5: Write and run integration tests**
  Test admin authentication, validation of rate bounds, and CRUD flow via MockMvc.
  Run: `./mvnw test -Dtest=AdminCommissionControllerIntegrationTest,CommissionSettingServiceImplTest`
  Expected: PASS
- [ ] **Step 6: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/commission/ src/test/java/com/aitechmarketplace/backend/commission/
  git commit -m "feat(commission): implement admin commission settings APIs and precedence resolution"
  ```

---

### Task 3: Transactional Checkout Settlement Snapshotting & Cancellation Rollback

**Files:**
- Modify: `src/main/java/com/aitechmarketplace/backend/order/service/CheckoutTransactionService.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/order/service/OrderServiceImpl.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/order/dto/OrderDetailResponse.java`
- Test: `src/test/java/com/aitechmarketplace/backend/order/service/CheckoutTransactionServiceSettlementTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/order/service/OrderCancellationSettlementTest.java`

**Interfaces:**
- Consumes: `CommissionSettingService.resolveCommissionRate(Long sellerId, Long categoryId)`
- Produces: Atomically populated `SellerOrderSettlement` records upon `createOrder()`.
- Updates: Transitions settlements to `CANCELLED` upon `cancelOrder()`.

- [ ] **Step 1: Write failing test for settlement creation during checkout**
  Verify that when an order with 2 items from different sellers is checked out, exactly 2 `SellerOrderSettlement` records are persisted with accurate `commission_rate`, `commission_amount`, `seller_net_amount`, and `status = PENDING`.
- [ ] **Step 2: Update `CheckoutTransactionService`**
  Inject `CommissionSettingService` and `SellerOrderSettlementRepository`. For each cart item in the checkout loop:
  - Query seller and category from product.
  - Compute `commissionRate = commissionSettingService.resolveCommissionRate(product.getSellerId(), product.getCategoryId())`.
  - Calculate `commissionAmount = itemSubtotal.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP)`.
  - Calculate `sellerNetAmount = itemSubtotal.subtract(commissionAmount)`.
  - Persist `SellerOrderSettlement`.
- [ ] **Step 3: Update `OrderServiceImpl.cancelOrder()`**
  When order cancellation succeeds, find all settlements for the order and set `status = SettlementStatus.CANCELLED`.
- [ ] **Step 4: Run tests to verify passing**
  Run: `./mvnw test -Dtest=CheckoutTransactionServiceSettlementTest,OrderCancellationSettlementTest,OrderCheckoutIntegrationTest`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/order/ src/test/java/com/aitechmarketplace/backend/order/
  git commit -m "feat(order): snapshot seller order settlements during checkout and void on cancellation"
  ```

---

### Task 4: Platform Administration Analytics Module

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/dto/admin/PlatformAnalyticsOverviewResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/dto/admin/TopSellerResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/dto/admin/CategoryGmvResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/dto/admin/RevenueTimelinePoint.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/dto/admin/PlatformRevenueChartResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/service/AdminAnalyticsService.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/service/AdminAnalyticsServiceImpl.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/controller/AdminAnalyticsController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/analytics/service/AdminAnalyticsServiceImplTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/analytics/controller/AdminAnalyticsControllerIntegrationTest.java`

**Interfaces:**
- Produces: `AdminAnalyticsService`:
  - `PlatformAnalyticsOverviewResponse getOverview(LocalDate startDate, LocalDate endDate)`
  - `List<TopSellerResponse> getTopSellers(LocalDate startDate, LocalDate endDate, int limit)`
  - `List<CategoryGmvResponse> getCategoryBreakdown(LocalDate startDate, LocalDate endDate)`
  - `PlatformRevenueChartResponse getRevenueChart(LocalDate startDate, LocalDate endDate, String interval)`
- Produces Endpoints:
  - `GET /api/v1/admin/analytics/overview`
  - `GET /api/v1/admin/analytics/top-sellers`
  - `GET /api/v1/admin/analytics/top-categories`
  - `GET /api/v1/admin/analytics/revenue-chart`

- [ ] **Step 1: Write unit tests for `AdminAnalyticsService`**
  Mock repository aggregations to assert GMV sum, commission total, order counts, cancellation rate percentage, and top sellers ranking.
- [ ] **Step 2: Add custom aggregation queries to repositories**
  Add `@Query` methods on `OrderRepository` and `SellerOrderSettlementRepository` for date-bounded GMV, platform revenue, and top seller sums.
- [ ] **Step 3: Implement `AdminAnalyticsServiceImpl`**
  Implement logic for period parsing (`7d`, `30d`, `this_month`, `last_month`) and date boundary validation ($\le 365$ days).
- [ ] **Step 4: Implement `AdminAnalyticsController`**
  Expose endpoints protected with `@PreAuthorize("hasRole('ADMIN')")`.
- [ ] **Step 5: Run integration tests**
  Run: `./mvnw test -Dtest=AdminAnalyticsControllerIntegrationTest,AdminAnalyticsServiceImplTest`
  Expected: PASS
- [ ] **Step 6: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/analytics/ src/test/java/com/aitechmarketplace/backend/analytics/
  git commit -m "feat(analytics): implement platform admin financial analytics and leaderboard endpoints"
  ```

---

### Task 5: Merchant / Seller Analytics Module

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/dto/seller/SellerAnalyticsOverviewResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/dto/seller/TopSellerProductResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/dto/seller/SellerSettlementResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/service/SellerAnalyticsService.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/service/SellerAnalyticsServiceImpl.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/controller/SellerAnalyticsController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/analytics/service/SellerAnalyticsServiceImplTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/analytics/controller/SellerAnalyticsControllerIntegrationTest.java`

**Interfaces:**
- Produces: `SellerAnalyticsService`:
  - `SellerAnalyticsOverviewResponse getOverview(Long sellerId, LocalDate startDate, LocalDate endDate)`
  - `List<TopSellerProductResponse> getTopProducts(Long sellerId, LocalDate startDate, LocalDate endDate, int limit)`
  - `Page<SellerSettlementResponse> getSettlementLedger(Long sellerId, SettlementStatus status, Pageable pageable)`
- Produces Endpoints:
  - `GET /api/v1/seller/analytics/overview`
  - `GET /api/v1/seller/analytics/top-products`
  - `GET /api/v1/seller/analytics/order-settlements`

- [ ] **Step 1: Write unit tests for `SellerAnalyticsService`**
  Verify gross sales, commission deduction, net earnings, pending settlement vs available balance calculations for the seller.
- [ ] **Step 2: Add seller aggregation queries to `SellerOrderSettlementRepository`**
  Add queries for `SUM(itemSubtotal)`, `SUM(commissionAmount)`, `SUM(sellerNetAmount)` filtered by `sellerId`, `status`, and `createdAt` range.
- [ ] **Step 3: Implement `SellerAnalyticsServiceImpl`**
  Enforce seller ID scoping so no merchant can view another's ledger or metrics.
- [ ] **Step 4: Implement `SellerAnalyticsController`**
  Extract authenticated user ID from `Authentication` context and enforce `@PreAuthorize("hasRole('SELLER')")`.
- [ ] **Step 5: Run integration tests**
  Run: `./mvnw test -Dtest=SellerAnalyticsControllerIntegrationTest,SellerAnalyticsServiceImplTest`
  Expected: PASS
- [ ] **Step 6: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/analytics/ src/test/java/com/aitechmarketplace/backend/analytics/
  git commit -m "feat(analytics): implement seller store earnings, escrow ledger, and top products APIs"
  ```

---

### Task 6: Customer Personal Spending Analytics Module

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/dto/customer/CustomerSpendingSummaryResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/dto/customer/CategorySpendingResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/dto/customer/MonthlySpendingResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/service/CustomerAnalyticsService.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/service/CustomerAnalyticsServiceImpl.java`
- Create: `src/main/java/com/aitechmarketplace/backend/analytics/controller/CustomerAnalyticsController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/analytics/service/CustomerAnalyticsServiceImplTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/analytics/controller/CustomerAnalyticsControllerIntegrationTest.java`

**Interfaces:**
- Produces: `CustomerAnalyticsService`:
  - `CustomerSpendingSummaryResponse getSpendingSummary(Long userId)`
  - `List<CategorySpendingResponse> getCategoryBreakdown(Long userId)`
  - `MonthlySpendingResponse getMonthlySpending(Long userId, int months)`
- Produces Endpoints:
  - `GET /api/v1/customer/analytics/spending-summary`
  - `GET /api/v1/customer/analytics/category-breakdown`
  - `GET /api/v1/customer/analytics/monthly-spending`

- [ ] **Step 1: Write unit tests for `CustomerAnalyticsService`**
  Verify aggregation of total spent, total saved via coupons, category distribution percentages, and monthly trajectory points.
- [ ] **Step 2: Add customer spending queries to `OrderRepository`**
  Add queries for user-specific total spend, discount sums, and category aggregations joined through `order_items` and `products`.
- [ ] **Step 3: Implement `CustomerAnalyticsServiceImpl`**
  Calculate promotional savings and round percentage distributions.
- [ ] **Step 4: Implement `CustomerAnalyticsController`**
  Protect with `@PreAuthorize("isAuthenticated()")` and bind to authenticated user ID.
- [ ] **Step 5: Run integration tests**
  Run: `./mvnw test -Dtest=CustomerAnalyticsControllerIntegrationTest,CustomerAnalyticsServiceImplTest`
  Expected: PASS
- [ ] **Step 6: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/analytics/ src/test/java/com/aitechmarketplace/backend/analytics/
  git commit -m "feat(analytics): implement customer spending summary, savings, and category distribution APIs"
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
  Document new endpoints under `/api/v1/admin/commission-settings`, `/api/v1/admin/analytics`, `/api/v1/seller/analytics`, and `/api/v1/customer/analytics`.
- [ ] **Step 3: Commit and verify clean working tree**
  ```bash
  git add README.md
  git commit -m "docs: document commission settings and multi-tier analytics in backend README"
  ```
