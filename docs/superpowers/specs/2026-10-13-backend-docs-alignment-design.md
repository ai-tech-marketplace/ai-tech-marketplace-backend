# Backend to Docs Alignment Design Specification

## 1. Intent & Purpose
The goal of this project is to bring `ai-tech-marketplace-backend` into complete alignment with the official system specification and API contracts defined in `ai-tech-marketplace-docs`. Because the frontend is not yet implemented, breaking changes to early backend APIs and direct restructuring of initial database migrations (V1–V6) are permitted and encouraged to establish a clean, production-grade foundation.

---

## 2. Goals, Non-Goals & Anti-Goals

### 2.1. Goals
1. **Clean Database Baseline (V1–V6)**:
   - Restructure migrations V1 through V6 so table definitions match `ai-tech-marketplace-docs/database/schema.md` directly.
   - Introduce `brands` table into the baseline sequence before `products`.
   - Add missing columns across core entities (`users.full_name`, `users.phone`, `users.status`; `products.sku`, `products.slug`, `products.brand_id`, `products.category_id`, `products.specifications`, `products.status`; `categories.slug`; `product_images.is_primary`).
   - Standardize system roles to `CUSTOMER`, `SELLER`, `ADMIN`.
2. **Standard API Envelope & Global Error Handling**:
   - Every endpoint returns `ApiResponse<T>` with `{ success: true, data: T, message: String }`.
   - `GlobalExceptionHandler` returns standard error format: `{ success: false, data: null, message: String, errorCode: String }` with appropriate HTTP status codes.
3. **API Routing & Versioning**:
   - All REST controllers adopt the base path `/api/v1/...`.
   - Support both public/seller endpoints and dedicated administrative endpoints (`/api/v1/admin/...`).
4. **Security & Public Access**:
   - `SecurityConfig` permits unauthenticated access to public catalog browsing (`GET /api/v1/products/**`, `GET /api/v1/categories/**`, `GET /api/v1/brands/**`) and authentication (`/api/v1/auth/**`).
   - Role-based authorization for administrative endpoints (`ROLE_ADMIN`).
5. **Brand Management Module**:
   - Complete implementation of Brand entity, repository, service, DTOs, and controller endpoints (`GET /api/v1/brands`, `GET /api/v1/brands/{id}`, `/api/v1/admin/brands/**`).
6. **Cart Enhancements**:
   - Implement `POST /api/v1/cart/merge` for merging guest carts upon authentication (`BR-CART-005`).
   - Include dynamic availability validation in cart item retrieval (`BR-CART-004`).
7. **Complete Test Integrity**:
   - Update all unit and integration tests to pass 100% against the new schema and API contracts.

### 2.2. Non-Goals
- Phase 3 checkout & order management (`POST /api/v1/orders`), payments, or coupons redemption.
- Apache Kafka event streaming.
- Python FastAPI AI integration or pgvector semantic search.

### 2.3. Anti-Goals
- Do not maintain backwards compatibility shims or duplicate endpoints (`/api/...` vs `/api/v1/...`). Cleanly standardize exclusively on `/api/v1/...`.
- Do not introduce non-standard table columns outside `database/schema.md`.

---

## 3. Architecture & Technical Decisions

### 3.1. Database Migration Strategy (Clean Reset)
The migration scripts in `src/main/resources/db/migration/` are restructured cleanly as follows:

1. **`V1__init.sql`**:
   - `users`: `id BIGSERIAL PRIMARY KEY`, `email VARCHAR(255) UNIQUE NOT NULL`, `password_hash VARCHAR(255) NOT NULL`, `full_name VARCHAR(100) NOT NULL`, `phone VARCHAR(30) NULL`, `status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`, `updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`.
   - `roles`: `id BIGSERIAL PRIMARY KEY`, `name VARCHAR(50) UNIQUE NOT NULL`. Seed roles: `CUSTOMER`, `SELLER`, `ADMIN`.
   - `user_roles`: `user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE`, `role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE`, `PRIMARY KEY (user_id, role_id)`.
2. **`V2__create_categories.sql`**:
   - `categories`: `id BIGSERIAL PRIMARY KEY`, `name VARCHAR(100) NOT NULL`, `slug VARCHAR(150) UNIQUE NOT NULL`, `description TEXT`, `status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`, `updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`.
3. **`V3__create_brands.sql`**:
   - `brands`: `id BIGSERIAL PRIMARY KEY`, `name VARCHAR(100) NOT NULL`, `slug VARCHAR(150) UNIQUE NOT NULL`, `status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`, `updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`.
4. **`V4__create_products.sql`**:
   - `products`: `id BIGSERIAL PRIMARY KEY`, `seller_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT`, `sku VARCHAR(100) UNIQUE NOT NULL`, `name VARCHAR(255) NOT NULL`, `slug VARCHAR(300) UNIQUE NOT NULL`, `description TEXT`, `price NUMERIC(15,2) NOT NULL CHECK (price >= 0)`, `stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0)`, `category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT`, `brand_id BIGINT REFERENCES brands(id) ON DELETE SET NULL`, `specifications JSONB`, `status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`, `updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`.
   - Indexes on `seller_id`, `category_id`, `brand_id`, `sku`, `slug`, `status`.
5. **`V5__create_product_images.sql`**:
   - `product_images`: `id BIGSERIAL PRIMARY KEY`, `product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE`, `object_key VARCHAR(500) UNIQUE NOT NULL`, `original_filename VARCHAR(255) NOT NULL`, `content_type VARCHAR(100) NOT NULL`, `file_size BIGINT NOT NULL CHECK (file_size >= 0)`, `display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0)`, `is_primary BOOLEAN NOT NULL DEFAULT FALSE`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`, `updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`.
6. **`V6__create_carts.sql`**:
   - `carts`: `id BIGSERIAL PRIMARY KEY`, `user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`, `updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`.
   - `cart_items`: `id BIGSERIAL PRIMARY KEY`, `cart_id BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE`, `product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE`, `quantity INTEGER NOT NULL CHECK (quantity > 0)`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`, `updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`. Unique constraint `(cart_id, product_id)`.

---

### 3.2. Standard Response & Error Envelope
1. **Success Response**:
   ```json
   {
     "success": true,
     "data": <Payload>,
     "message": <Optional message or null>
   }
   ```
2. **Error Response**:
   ```json
   {
     "success": false,
     "data": null,
     "message": "Human readable error description",
     "errorCode": "SPECIFIC_ERROR_CODE"
   }
   ```
3. **Error Code Mapping**:
   - `400 Bad Request`: `BAD_REQUEST`, `INVALID_REQUEST_PAYLOAD`, `QUANTITY_INVALID`
   - `401 Unauthorized`: `AUTH_INVALID_CREDENTIALS`, `UNAUTHORIZED`
   - `403 Forbidden`: `FORBIDDEN`, `INSUFFICIENT_PERMISSIONS`
   - `404 Not Found`: `USER_NOT_FOUND`, `PRODUCT_NOT_FOUND`, `CATEGORY_NOT_FOUND`, `BRAND_NOT_FOUND`, `CART_ITEM_NOT_FOUND`
   - `409 Conflict`: `USER_ALREADY_EXISTS`, `SKU_ALREADY_EXISTS`, `SLUG_ALREADY_EXISTS`, `RESOURCE_IN_USE`
   - `500 Internal Error`: `INTERNAL_SERVER_ERROR`

---

### 3.3. Security Configuration
- **Public Endpoints (`permitAll`)**:
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `GET /api/v1/products/**` (catalog, detail, images content)
  - `GET /api/v1/categories/**`
  - `GET /api/v1/brands/**`
  - `/actuator/health/**`
- **Authenticated Customer Endpoints**:
  - `GET /api/v1/auth/me`
  - `/api/v1/cart/**`
- **Seller Endpoints**:
  - `GET /api/v1/products/my`
  - `POST /api/v1/products` (Seller/Admin create)
  - `PUT /api/v1/products/{id}` (Owner/Admin update)
  - `DELETE /api/v1/products/{id}` (Owner/Admin deactivate)
  - `/api/v1/products/{productId}/images/**` (Owner/Admin image upload/delete)
- **Admin Only Endpoints (`hasRole('ADMIN')`)**:
  - `/api/v1/admin/**` (categories, brands, products)

---

### 3.4. Module Contracts & Specific Builder Decisions

#### A. Authentication & User (`/api/v1/auth`)
- `POST /api/v1/auth/register`:
  - Request: `{ email, password, fullName, phone }`
  - Creates user with role `CUSTOMER`.
  - Response: `ApiResponse<UserResponse>` where `UserResponse` has `{ id, email, fullName, phone, role: "CUSTOMER" }`.
- `POST /api/v1/auth/login`:
  - Request: `{ email, password }`
  - Response: `ApiResponse<LoginResponse>`:
    ```json
    {
      "accessToken": "jwt-token",
      "refreshToken": "jwt-token-or-uuid",
      "tokenType": "Bearer",
      "expiresIn": 3600,
      "user": {
        "id": 1,
        "email": "user@example.com",
        "fullName": "John Doe",
        "role": "CUSTOMER"
      }
    }
    ```
- `GET /api/v1/auth/me`:
  - Returns authenticated user details wrapped in `ApiResponse<UserResponse>`.

#### B. Categories (`/api/v1/categories`, `/api/v1/admin/categories`)
- `GET /api/v1/categories`: Public list of active categories `[{ id, name, slug, description }]`.
- `GET /api/v1/categories/{id}`: Public category detail.
- `POST /api/v1/admin/categories`: Admin create `{ name, slug, description }`. Auto-generates slug from name if omitted.
- `PUT /api/v1/admin/categories/{id}`: Admin update `{ name, slug, description }`.
- `DELETE /api/v1/admin/categories/{id}`: Soft delete (`status = 'INACTIVE'`). *Builder call*: If active products reference this category, reject with 409 Conflict (`RESOURCE_IN_USE`) to prevent catalog inconsistencies.

#### C. Brands (`/api/v1/brands`, `/api/v1/admin/brands`)
- `GET /api/v1/brands`: Public list of active brands `[{ id, name, slug, status }]`.
- `GET /api/v1/brands/{id}`: Public brand detail.
- `POST /api/v1/admin/brands`: Admin create `{ name, slug }`. Auto-generates slug from name if omitted.
- `PUT /api/v1/admin/brands/{id}`: Admin update `{ name, slug, status }`.
- `DELETE /api/v1/admin/brands/{id}`: Soft delete (`status = 'INACTIVE'`). *Builder call*: If active products reference this brand, reject with 409 Conflict (`RESOURCE_IN_USE`).

#### D. Products & Images (`/api/v1/products`, `/api/v1/admin/products`)
- **Slug Generation**: *Builder call*: If client provides `slug`, validate uniqueness; if omitted in create request, auto-generate slug from `name` (with alphanumeric hyphenation and deduplication suffix).
- **Seller Attribution**: *Builder call*: When Admin creates product via `/api/v1/admin/products`, `sellerId` can optionally be passed in payload; if omitted or if created via `/api/v1/products` by a seller, defaults to authenticated user's ID.
- **Specification Storage**: Stored as PostgreSQL `JSONB` via Jackson converter (`Map<String, Object>`), serialized cleanly in responses.
- **Image URL & Primary Image**:
  - `thumbnailUrl` resolves to `/api/v1/products/{id}/images/{primaryOrFirstImageId}/content`.
  - Image upload `/api/v1/products/{productId}/images` accepts `file` (MultipartFile) and optional `isPrimary` (boolean, default false). If true, sets other images of that product to `is_primary = false`.
- **Product List Response**:
  - `ApiResponse<Page<ProductSummaryResponse>>`: item contains `{ id, sku, name, slug, price, brand: { id, name }, category: { id, name }, thumbnailUrl, status }`.
- **Product Detail Response**:
  - `ApiResponse<ProductDetailResponse>`: contains `{ id, sku, name, slug, description, price, brand: { id, name }, category: { id, name }, specifications, inventory: { available: stock_quantity }, images: [url] }`.

#### E. Shopping Cart (`/api/v1/cart`)
- **Staleness & Availability**: *Builder call*: `cart_items` table adheres strictly to `schema.md` (no historical added price column). In `CartItemResponse`:
  - `isAvailable`: dynamic boolean evaluated as `product.status == 'ACTIVE' && product.stockQuantity >= item.quantity`.
  - Subtotal and total amounts are calculated from current live `product.price * quantity`.
- **Guest Cart Merge (`POST /api/v1/cart/merge`)**:
  - *Builder call (Reconciliation strategy)*: Accepts `items: [{ productId, quantity }]`. For each item:
    - If product does not exist or `status != 'ACTIVE'`, ignore item (do not abort entire login).
    - If requested quantity + existing cart quantity > `stockQuantity`, clamp the added quantity to available stock.
    - If available stock is 0, ignore item.
    - Returns updated `ApiResponse<CartResponse>`.

---

## 4. Execution Plan Summary
1. **Reset Migrations & Entities**:
   - Update V1–V6 SQL files.
   - Update `User`, `Role`, `Category`, `Brand` (new), `Product`, `ProductImage`, `Cart`, `CartItem`.
2. **API & Security Foundation**:
   - `ApiResponse<T>`, `ErrorResponse`, `GlobalExceptionHandler`.
   - Update `SecurityConfig` (public catalog permitAll, `/api/v1/**` routing).
3. **Auth & User Module**:
   - Update DTOs, `AuthService`, `AuthController` (`/api/v1/auth`), `UserController`.
4. **Category & Brand Modules**:
   - Implement `Brand` module end-to-end.
   - Update `Category` with `slug` and `/api/v1/admin/categories`.
5. **Product & Gallery Module**:
   - Update `Product` DTOs, service, controller (`/api/v1/products` and `/api/v1/admin/products`).
   - JSONB specifications mapping and image URLs.
6. **Cart Module**:
   - Implement `merge` endpoint and availability validation.
7. **Test Suite Verification**:
   - Update integration test suites (`AuthControllerIntegrationTest`, `CategoryControllerIntegrationTest`, `ProductControllerIntegrationTest`, `CartControllerIntegrationTest`, and add `BrandControllerIntegrationTest`).
   - Run `./mvnw test` to achieve 100% test pass rate.
