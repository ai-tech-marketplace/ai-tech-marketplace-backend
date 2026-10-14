# Backend to Docs Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `ai-tech-marketplace-backend` to align completely with `ai-tech-marketplace-docs` by restructuring baseline Flyway migrations V1–V6, standardizing `/api/v1` routes and `ApiResponse<T>` envelopes, enabling public catalog access, implementing the Brand module, and synchronizing Auth, Category, Product, and Cart contracts.

**Architecture:** Spring Boot 3.4 layered architecture (PostgreSQL, Flyway, Spring Data JPA, Spring Security with stateless JWT, Jackson JSONB, S3 storage). All controllers expose REST endpoints under `/api/v1` wrapped in `ApiResponse<T>` with standardized `GlobalExceptionHandler` error payloads.

**Tech Stack:** Java 21, Spring Boot 3.4.3, Spring Data JPA, Spring Security, PostgreSQL, Flyway, AWS S3 SDK, JUnit 5, MockMvc, AssertJ.

**Spec:** `/home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-backend/docs/superpowers/specs/2026-10-13-backend-docs-alignment-design.md`

## Global Constraints

- All REST endpoints must use the prefix `/api/v1/...` (no legacy `/api/...` paths).
- Every successful response envelope must use `ApiResponse<T>` with fields `success: true`, `data: T`, and `message: String`.
- Every error response envelope must use `ErrorResponse` with fields `success: false`, `data: null`, `message: String`, and `errorCode: String`.
- PostgreSQL database baseline migrations must be restructured as V1 through V6 without temporary migration patches.
- Roles are strictly `CUSTOMER`, `SELLER`, `ADMIN`.
- Public catalog endpoints (`GET /api/v1/products/**`, `GET /api/v1/categories/**`, `GET /api/v1/brands/**`) must be accessible without authentication.
- All tests must pass via `./mvnw test`.

## Review Focus

1. **Unauthenticated Catalog Access**: Calling `GET /api/v1/products` and `GET /api/v1/categories` without an Authorization header returns HTTP 200 with catalog data rather than 401 Unauthorized.
2. **Standard Error Envelope on Validation & Exceptions**: Any client validation failure (e.g., negative price, invalid email) or entity not found throws an exception that `GlobalExceptionHandler` formats into `{ success: false, data: null, message, errorCode }` instead of Spring default maps.
3. **Product Slug Collision Handling**: Creating a product with a duplicate name or slug auto-resolves with a deduplication suffix or returns a clean 409 Conflict with `errorCode: "SLUG_ALREADY_EXISTS"`.
4. **Guest Cart Merge Stock Clamping**: When merging guest cart items via `POST /api/v1/cart/merge`, requested quantities exceeding available stock are clamped to available stock, and out-of-stock/inactive items are safely skipped rather than aborting the login flow.
5. **Brand & Category In-Use Protection**: Deleting/deactivating a Category or Brand currently referenced by active products returns HTTP 409 Conflict with `errorCode: "RESOURCE_IN_USE"`.

---

### Task 1: Clean Flyway Migrations (V1–V6)

**Files:**
- Modify: `src/main/resources/db/migration/V1__init.sql`
- Modify: `src/main/resources/db/migration/V2__create_products.sql` -> rename/reorder to `V2__create_categories.sql`
- Modify: `src/main/resources/db/migration/V3__create_categories.sql` -> rename/reorder to `V3__create_brands.sql`
- Modify: `src/main/resources/db/migration/V4__create_product_categories.sql` -> rename/replace with `V4__create_products.sql`
- Modify: `src/main/resources/db/migration/V5__create_product_images.sql`
- Modify: `src/main/resources/db/migration/V6__create_carts.sql`

**Interfaces:**
- Produces: Tables `users` (with `full_name`, `phone`, `status`), `roles` (`CUSTOMER`, `SELLER`, `ADMIN`), `categories` (with `slug`, `status`), `brands` (with `slug`, `status`), `products` (with `sku`, `slug`, `brand_id`, `category_id`, `specifications`, `status`), `product_images` (with `is_primary`), `carts`, and `cart_items`.

- [ ] **Step 1: Write restructured `V1__init.sql`**
  Add `full_name VARCHAR(100) NOT NULL`, `phone VARCHAR(30)`, `status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'` to `users`. Seed roles `CUSTOMER`, `SELLER`, `ADMIN`.
- [ ] **Step 2: Write `V2__create_categories.sql`**
  Create `categories` with `id BIGSERIAL PRIMARY KEY`, `name VARCHAR(100) NOT NULL`, `slug VARCHAR(150) UNIQUE NOT NULL`, `description TEXT`, `status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'`, `created_at`, `updated_at`.
- [ ] **Step 3: Write `V3__create_brands.sql`**
  Create `brands` with `id BIGSERIAL PRIMARY KEY`, `name VARCHAR(100) NOT NULL`, `slug VARCHAR(150) UNIQUE NOT NULL`, `status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'`, `created_at`, `updated_at`.
- [ ] **Step 4: Write `V4__create_products.sql`**
  Create `products` with `id BIGSERIAL PRIMARY KEY`, `seller_id BIGINT NOT NULL REFERENCES users(id)`, `sku VARCHAR(100) UNIQUE NOT NULL`, `name VARCHAR(255) NOT NULL`, `slug VARCHAR(300) UNIQUE NOT NULL`, `description TEXT`, `price NUMERIC(15,2) NOT NULL CHECK (price >= 0)`, `stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0)`, `category_id BIGINT NOT NULL REFERENCES categories(id)`, `brand_id BIGINT REFERENCES brands(id) ON DELETE SET NULL`, `specifications JSONB`, `status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'`, `created_at`, `updated_at`.
  (Remove separate join table `product_categories`).
- [ ] **Step 5: Write `V5__create_product_images.sql`**
  Add `is_primary BOOLEAN NOT NULL DEFAULT FALSE` to `product_images`.
- [ ] **Step 6: Write `V6__create_carts.sql`**
  Verify `carts` and `cart_items` match `schema.md` definitions.
- [ ] **Step 7: Verify SQL syntax and commit**
  ```bash
  git add src/main/resources/db/migration/
  git commit -m "refactor(db): restructure V1-V6 baseline migrations to match schema.md"
  ```

---

### Task 2: Standard API Envelope & Global Error Handling

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/common/dto/ErrorResponse.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/common/dto/ApiResponse.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/common/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/aitechmarketplace/backend/common/exception/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces: `ErrorResponse(boolean success, Object data, String message, String errorCode)`
- Produces: `GlobalExceptionHandler` returning `ResponseEntity<ErrorResponse>` for `NotFoundException`, `BadRequestException`, `ConflictException`, `ForbiddenException`, `MethodArgumentNotValidException`.

- [ ] **Step 1: Write test for GlobalExceptionHandler error formatting**
  Test that `handleNotFoundException` returns status 404, `success = false`, `data = null`, and `errorCode = "RESOURCE_NOT_FOUND"` or specific code.
- [ ] **Step 2: Run test to verify it fails**
  Run: `./mvnw test -Dtest=GlobalExceptionHandlerTest`
- [ ] **Step 3: Implement `ErrorResponse` record and update `GlobalExceptionHandler`**
  Implement `ErrorResponse(boolean success, Object data, String message, String errorCode)` and update all exception handlers in `GlobalExceptionHandler` to return `ResponseEntity<ErrorResponse>`.
- [ ] **Step 4: Run test to verify it passes**
  Run: `./mvnw test -Dtest=GlobalExceptionHandlerTest`
- [ ] **Step 5: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/common/ src/test/java/com/aitechmarketplace/backend/common/
  git commit -m "feat(common): standardize ErrorResponse and GlobalExceptionHandler"
  ```

---

### Task 3: Security & Public Catalog Configuration

**Files:**
- Modify: `src/main/java/com/aitechmarketplace/backend/config/SecurityConfig.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/auth/security/JwtAuthenticationFilter.java`
- Test: `src/test/java/com/aitechmarketplace/backend/config/SecurityConfigTest.java`

**Interfaces:**
- Produces: Updated `SecurityFilterChain` permitting `/api/v1/auth/register`, `/api/v1/auth/login`, `GET /api/v1/products/**`, `GET /api/v1/categories/**`, `GET /api/v1/brands/**`.

- [ ] **Step 1: Write security integration test**
  Verify `GET /api/v1/products` returns 200 without Authorization header; verify `/api/v1/admin/**` returns 401/403 without `ROLE_ADMIN`.
- [ ] **Step 2: Update `SecurityConfig.java`**
  Update `authorizeHttpRequests` with `/api/v1/...` routes and public catalog permissions.
- [ ] **Step 3: Run security test to verify it passes**
  Run: `./mvnw test -Dtest=SecurityConfigTest`
- [ ] **Step 4: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/config/ src/test/java/com/aitechmarketplace/backend/config/
  git commit -m "feat(security): allow public catalog browsing and configure /api/v1 paths"
  ```

---

### Task 4: Auth & User Module Alignment

**Files:**
- Modify: `src/main/java/com/aitechmarketplace/backend/user/entity/RoleName.java` (values `CUSTOMER`, `SELLER`, `ADMIN`)
- Modify: `src/main/java/com/aitechmarketplace/backend/user/entity/User.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/auth/dto/RegisterRequest.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/auth/dto/LoginResponse.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/auth/dto/UserResponse.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/user/service/UserServiceImpl.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/auth/service/AuthServiceImpl.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/auth/controller/AuthController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/auth/controller/AuthControllerIntegrationTest.java`

**Interfaces:**
- Produces: `POST /api/v1/auth/register` returning `ApiResponse<UserResponse>`
- Produces: `POST /api/v1/auth/login` returning `ApiResponse<LoginResponse>` with `{ accessToken, refreshToken, tokenType, expiresIn, user }`
- Produces: `GET /api/v1/auth/me` returning `ApiResponse<UserResponse>`

- [ ] **Step 1: Update `RoleName`, `User` entity, and DTOs**
  Change `RoleName` enum to `CUSTOMER`, `SELLER`, `ADMIN`. Update `User` with `fullName`, `phone`, `status`. Update `RegisterRequest` (`email`, `password`, `fullName`, `phone`).
- [ ] **Step 2: Update `AuthService` and `UserService`**
  Assign default `CUSTOMER` role upon registration.
- [ ] **Step 3: Update `AuthController`**
  Set `@RequestMapping("/api/v1/auth")`, add `@GetMapping("/me")`, wrap all returns in `ApiResponse.success(...)`.
- [ ] **Step 4: Update unit and integration tests**
  Update `AuthServiceImplTest` and `AuthControllerIntegrationTest` to verify `/api/v1/auth/register`, `/api/v1/auth/login`, and `/api/v1/auth/me`.
- [ ] **Step 5: Run tests to verify they pass**
  Run: `./mvnw test -Dtest=AuthControllerIntegrationTest,AuthServiceImplTest,UserServiceImplTest`
- [ ] **Step 6: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/user/ src/main/java/com/aitechmarketplace/backend/auth/ src/test/java/
  git commit -m "refactor(auth): align user roles, DTOs, and /api/v1/auth endpoints with docs"
  ```

---

### Task 5: Category Module Alignment

**Files:**
- Modify: `src/main/java/com/aitechmarketplace/backend/category/entity/Category.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/category/dto/CategoryCreateRequest.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/category/dto/CategoryUpdateRequest.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/category/dto/CategoryResponse.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/category/service/CategoryService.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/category/service/CategoryServiceImpl.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/category/controller/CategoryController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/category/controller/CategoryControllerIntegrationTest.java`

**Interfaces:**
- Produces: `GET /api/v1/categories`, `GET /api/v1/categories/{id}`
- Produces: `POST /api/v1/admin/categories`, `PUT /api/v1/admin/categories/{id}`, `DELETE /api/v1/admin/categories/{id}`
- All returning `ApiResponse<T>`

- [ ] **Step 1: Update `Category` entity and DTOs with `slug` and `status`**
  Add `slug` field and unique constraint; `CategoryResponse` returns `(id, name, slug, description)`.
- [ ] **Step 2: Implement auto-slug and in-use check in `CategoryServiceImpl`**
  Generate slug if null; check if active products exist before deactivation (`ConflictException("RESOURCE_IN_USE")`).
- [ ] **Step 3: Update `CategoryController`**
  Map public routes to `/api/v1/categories` and admin routes to `/api/v1/admin/categories`. Wrap responses in `ApiResponse`.
- [ ] **Step 4: Update integration tests**
  Update `CategoryControllerIntegrationTest` with new `/api/v1` and `/api/v1/admin` paths and response format.
- [ ] **Step 5: Run tests to verify they pass**
  Run: `./mvnw test -Dtest=CategoryControllerIntegrationTest,CategoryServiceImplTest`
- [ ] **Step 6: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/category/ src/test/java/com/aitechmarketplace/backend/category/
  git commit -m "refactor(category): add slug, separate admin routes, and wrap in ApiResponse"
  ```

---

### Task 6: Brand Module Implementation

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/brand/entity/Brand.java`
- Create: `src/main/java/com/aitechmarketplace/backend/brand/repository/BrandRepository.java`
- Create: `src/main/java/com/aitechmarketplace/backend/brand/dto/BrandCreateRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/brand/dto/BrandUpdateRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/brand/dto/BrandResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/brand/service/BrandService.java`
- Create: `src/main/java/com/aitechmarketplace/backend/brand/service/BrandServiceImpl.java`
- Create: `src/main/java/com/aitechmarketplace/backend/brand/controller/BrandController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/brand/service/BrandServiceImplTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/brand/controller/BrandControllerIntegrationTest.java`

**Interfaces:**
- Produces: `GET /api/v1/brands`, `GET /api/v1/brands/{id}` (Public)
- Produces: `POST /api/v1/admin/brands`, `PUT /api/v1/admin/brands/{id}`, `DELETE /api/v1/admin/brands/{id}` (Admin)
- All returning `ApiResponse<T>`

- [ ] **Step 1: Create `Brand` entity, DTOs, and repository**
  Entity has `id`, `name`, `slug`, `status`, `createdAt`, `updatedAt`.
- [ ] **Step 2: Write unit and integration tests for Brand operations**
  Verify CRUD operations, slug uniqueness, and role authorization.
- [ ] **Step 3: Implement `BrandService` and `BrandServiceImpl`**
  Implement active listing, detail retrieval, creation with auto-slug, update, and deactivation.
- [ ] **Step 4: Implement `BrandController`**
  Map public routes to `/api/v1/brands` and admin routes to `/api/v1/admin/brands`.
- [ ] **Step 5: Run tests to verify they pass**
  Run: `./mvnw test -Dtest=BrandControllerIntegrationTest,BrandServiceImplTest`
- [ ] **Step 6: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/brand/ src/test/java/com/aitechmarketplace/backend/brand/
  git commit -m "feat(brand): implement Brand entity, service, controller and tests"
  ```

---

### Task 7: Product Catalog & Images Alignment

**Files:**
- Modify: `src/main/java/com/aitechmarketplace/backend/product/entity/Product.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/product/image/entity/ProductImage.java`
- Create: `src/main/java/com/aitechmarketplace/backend/product/converter/JsonbConverter.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/product/dto/ProductCreateRequest.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/product/dto/ProductResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/product/dto/ProductSummaryResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/product/dto/ProductDetailResponse.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/product/service/ProductService.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/product/service/ProductServiceImpl.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/product/controller/ProductController.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/product/image/controller/ProductImageController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/product/controller/ProductControllerIntegrationTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/product/image/controller/ProductImageControllerIntegrationTest.java`

**Interfaces:**
- Produces: `GET /api/v1/products` (summary items with `sku`, `slug`, `brand`, `category`, `thumbnailUrl`, `status`)
- Produces: `GET /api/v1/products/{id}` (detail with `specifications`, `inventory`, `images`)
- Produces: `POST /api/v1/products`, `POST /api/v1/admin/products`, `PUT /api/v1/products/{id}`, `DELETE /api/v1/products/{id}`
- Produces: `/api/v1/products/{productId}/images` with `isPrimary` support
- All returning `ApiResponse<T>`

- [ ] **Step 1: Update `Product` entity**
  Add `sku`, `slug`, `brand` (ManyToOne), `category` (ManyToOne), `specifications` (Map<String, Object>), `status`. Update `ProductImage` with `isPrimary`.
- [ ] **Step 2: Update Product DTOs**
  Create `ProductSummaryResponse` and `ProductDetailResponse` matching `docs/api/products.md`.
- [ ] **Step 3: Update `ProductService` & `ProductServiceImpl`**
  Implement search with brandId and categoryId filters; handle slug auto-generation; resolve thumbnail and image URLs.
- [ ] **Step 4: Update `ProductController` and `ProductImageController`**
  Map to `/api/v1/products` and `/api/v1/admin/products`. Wrap all responses in `ApiResponse`.
- [ ] **Step 5: Update tests**
  Update `ProductControllerIntegrationTest` and `ProductImageControllerIntegrationTest` to assert new fields and endpoints.
- [ ] **Step 6: Run tests to verify they pass**
  Run: `./mvnw test -Dtest=ProductControllerIntegrationTest,ProductImageControllerIntegrationTest,ProductServiceImplTest`
- [ ] **Step 7: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/product/ src/test/java/com/aitechmarketplace/backend/product/
  git commit -m "refactor(product): align product catalog, specifications, and gallery with docs"
  ```

---

### Task 8: Shopping Cart Alignment & Merge Endpoint

**Files:**
- Modify: `src/main/java/com/aitechmarketplace/backend/cart/dto/CartItemResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/cart/dto/CartMergeRequest.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/cart/service/CartService.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/cart/service/CartServiceImpl.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/cart/controller/CartController.java`
- Test: `src/test/java/com/aitechmarketplace/backend/cart/controller/CartControllerIntegrationTest.java`

**Interfaces:**
- Produces: `POST /api/v1/cart/merge`
- Produces: `CartItemResponse` with dynamic `isAvailable: boolean` check and `/api/v1` thumbnail URL

- [ ] **Step 1: Add `CartMergeRequest` and update `CartItemResponse`**
  `CartMergeRequest(List<CartItemAddRequest> items)`. In `CartItemResponse`, add `boolean isAvailable`.
- [ ] **Step 2: Implement `merge` method in `CartServiceImpl`**
  Implement reconciliation logic: iterate items, skip inactive/zero-stock products, clamp quantity to available stock, save to cart.
- [ ] **Step 3: Add `POST /merge` to `CartController`**
  Expose `@PostMapping("/merge")` mapped under `/api/v1/cart`.
- [ ] **Step 4: Update cart tests**
  Add unit and integration tests for guest cart merge reconciliation and availability flags.
- [ ] **Step 5: Run tests to verify they pass**
  Run: `./mvnw test -Dtest=CartControllerIntegrationTest,CartServiceImplTest`
- [ ] **Step 6: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/cart/ src/test/java/com/aitechmarketplace/backend/cart/
  git commit -m "feat(cart): implement guest cart merge and availability validation"
  ```

---

### Task 9: Full Test Suite Verification & Roadmap Sync

**Files:**
- Modify: `ai-tech-marketplace-docs/development/roadmap.md`

- [ ] **Step 1: Run complete maven test suite**
  Run: `./mvnw clean test`
  Verify: 100% test pass rate across all modules.
- [ ] **Step 2: Verify database migrations run cleanly on fresh database**
  Verify Flyway runs V1 through V6 without error.
- [ ] **Step 3: Update documentation roadmap**
  Ensure `ai-tech-marketplace-docs/development/roadmap.md` reflects true completed state for Brands and Catalog.
- [ ] **Step 4: Commit**
  ```bash
  git add ai-tech-marketplace-docs/development/roadmap.md
  git commit -m "docs(roadmap): synchronize roadmap with verified backend deliverables"
  ```
