# AI Tech Marketplace — Backend Service

The core transactional backend service for the **AI Tech Marketplace** platform. Built with **Java 21** and **Spring Boot 4**, this service manages identity and access control, catalog and inventory management, shopping carts, promotion engines, high-concurrency checkout pipelines, and Kafka-based event streaming.

---

## Technology Stack

* **Language & Framework**: Java 21, Spring Boot 4.1.1
* **Database & Persistence**: PostgreSQL 17, Spring Data JPA, Hibernate, Flyway Database Migrations (V1–V9)
* **Security & Auth**: Spring Security 6, Stateless JWT (`io.jsonwebtoken` 0.13.0), BCrypt password hashing
* **Caching & Distributed Locking**: Redis 7, Spring Data Redis (Lettuce), Spring Integration Redis (`RedisLockRegistry`)
* **Event Streaming & Messaging**: Apache Kafka 3.9 (KRaft mode), Spring Kafka with JsonSerializer/JsonDeserializer, Dead Letter Topic (DLT)
* **Media & Object Storage**: SeaweedFS (S3-compatible API), AWS SDK for Java v2 (`software.amazon.awssdk:s3`)
* **Observability & Tools**: Spring Boot Actuator, Project Lombok, Jakarta Bean Validation
* **Testing**: JUnit 5, Mockito, Spring Boot Test, Spring Kafka Test, Embedded / Mock test infrastructure

---

## System Architecture & Implemented Capabilities

### 1. High-Concurrency Checkout Pipeline
To ensure strict inventory integrity and eliminate race conditions:
* **Redis Distributed Locking**: Uses `RedisLockRegistry` to acquire a lock keyed by `checkout:user:{userId}`. The lock is managed in `OrderServiceImpl` **outside** the database transaction boundary, eliminating lock-holding during connection waits.
* **Pessimistic Database Locking**: Product stock quantities are locked at the database level using `LockModeType.PESSIMISTIC_WRITE` during stock reservation inside `CheckoutTransactionService`.
* **Atomic State Transition & Snapshotting**: Carts are validated against stock availability, snapshots are converted into immutable order items, active carts are cleared, and payment records are created within a single transactional unit.
* **Cancellation & Automatic Rollback**: Cancelling an eligible order automatically restores reserved inventory stock and releases redeemed coupon usage quotas (`POST /api/v1/orders/{id}/cancel`).

### 2. Event Streaming with Apache Kafka
* **Transactional Outbox / Domain Events**: Domain events (`OrderCreatedEvent`, `OrderCancelledEvent`) are published via `DomainEventPublisher` using `@TransactionalEventListener(phase = AFTER_COMMIT)`.
* **Ordering Guarantee**: Domain events use `userId.toString()` as the Kafka message key, guaranteeing strict partition-level FIFO message ordering per customer.
* **Resilient Consumers**: Background consumers for Notification and Analytics services process messages with retry backoff and route unrecoverable failures to a Dead Letter Topic (`order-events.DLT`).

### 3. Catalog & Search Management
* **Hierarchical Categories & Brands**: Slug-based navigation, active status filtering, and admin deletion protection for categories/brands currently linked to products.
* **Dynamic Specifications (JSONB)**: Flexible key-value tech specifications (RAM, CPU, GPU, storage, wattage) stored as PostgreSQL JSONB.
* **Multi-Criteria Search**: Paginated filtering by keyword, category, brand, and min/max price range.

### 4. Media Storage (SeaweedFS S3)
* Multipart file uploads via `/api/v1/products/{id}/images`.
* S3 bucket storage with automatic URL generation and primary gallery image selection.

### 5. Shopping Cart & Guest Merge
* Persistent customer shopping cart supporting add, update, remove, and clear actions.
* Real-time stock validation and clamping against current warehouse availability.
* Guest cart merge API (`POST /api/v1/cart/merge`) for seamless anonymous-to-authenticated transitions.

### 6. Dynamic Promotion & Coupon Engine
* Pre-checkout validation (`POST /api/v1/coupons/validate`) supporting fixed amount discounts, percentage discounts, minimum spend thresholds, and maximum discount caps.
* Dynamic shipping fee deduction rules (e.g. Free Shipping coupons).
* Per-user usage quotas and atomic quota incrementing/decrementing.

### 7. Standardized API Envelope & Error Handling
* All responses wrapped in `ApiResponse<T>`:
  ```json
  {
    "success": true,
    "data": { ... },
    "error": null,
    "timestamp": "2026-10-15T15:30:00Z"
  }
  ```
* Standardized `ErrorResponse` handled globally via `GlobalExceptionHandler` with explicit error codes (e.g. `INSUFFICIENT_STOCK`, `COUPON_EXPIRED`, `CONCURRENT_CHECKOUT_IN_PROGRESS`).

---

## Database Migrations (Flyway)

Database schema evolution is managed via Flyway migrations under `src/main/resources/db/migration/`:

| Migration | Target Tables / Entities | Description |
| :--- | :--- | :--- |
| `V1__init.sql` | `users`, `roles`, `user_roles` | User identities, credential hashes, and system roles (`CUSTOMER`, `SELLER`, `ADMIN`). |
| `V2__create_categories.sql` | `categories` | Product hierarchy with unique slugs and active flags. |
| `V3__create_brands.sql` | `brands` | Tech manufacturers and hardware brands. |
| `V4__create_products.sql` | `products` | Product listings, pricing, stock, JSONB specifications, and status. |
| `V5__create_product_images.sql` | `product_images` | Product gallery media URLs, display order, and primary image flag. |
| `V6__create_carts.sql` | `carts`, `cart_items` | Shopping cart sessions and quantity items. |
| `V7__create_addresses.sql` | `addresses` | Customer shipping addresses and default address markers. |
| `V8__create_coupons.sql` | `coupons`, `coupon_usages` | Promo codes, discount rules, quotas, and customer usage audit trail. |
| `V9__create_orders.sql` | `orders`, `order_items`, `payments` | Orders, immutable item snapshots, and payment records. |

---

## API Routes Overview

Base path: `/api/v1`

### Authentication & Users (`/api/v1/auth`)
* `POST /api/v1/auth/register` — Register customer or seller account
* `POST /api/v1/auth/login` — Authenticate and receive JWT token
* `GET  /api/v1/auth/me` — Retrieve current authenticated profile

### Categories & Brands (`/api/v1/categories`, `/api/v1/brands`)
* `GET  /api/v1/categories` — List active categories
* `GET  /api/v1/categories/{id}` — Get category detail
* `GET  /api/v1/brands` — List active brands
* `GET  /api/v1/brands/{id}` — Get brand detail
* `POST /api/v1/admin/categories` — Create category (`ADMIN`)
* `PUT  /api/v1/admin/categories/{id}` — Update category (`ADMIN`)
* `DELETE /api/v1/admin/categories/{id}` — Delete category with in-use check (`ADMIN`)
* `POST /api/v1/admin/brands` — Create brand (`ADMIN`)
* `PUT  /api/v1/admin/brands/{id}` — Update brand (`ADMIN`)
* `DELETE /api/v1/admin/brands/{id}` — Delete brand with in-use check (`ADMIN`)

### Products & Images (`/api/v1/products`)
* `GET    /api/v1/products` — Filter & search products (pagination, price, brand, category)
* `GET    /api/v1/products/{id}` — Get product details and specs
* `POST   /api/v1/products` — Create product (`SELLER`, `ADMIN`)
* `PUT    /api/v1/products/{id}` — Update product listing
* `PATCH  /api/v1/products/{id}/stock` — Restock / update product inventory
* `POST   /api/v1/products/{id}/images` — Upload product gallery image (`multipart/form-data`)
* `DELETE /api/v1/products/{id}/images/{imageId}` — Delete product image

### Shopping Cart (`/api/v1/cart`)
* `GET    /api/v1/cart` — Get current customer's cart
* `POST   /api/v1/cart/items` — Add product item to cart
* `PUT    /api/v1/cart/items/{itemId}` — Update item quantity
* `DELETE /api/v1/cart/items/{itemId}` — Remove item from cart
* `DELETE /api/v1/cart` — Clear all items in cart
* `POST   /api/v1/cart/merge` — Merge guest session cart into user cart

### Shipping Addresses (`/api/v1/addresses`)
* `GET    /api/v1/addresses` — List user's saved shipping addresses
* `POST   /api/v1/addresses` — Create shipping address
* `PUT    /api/v1/addresses/{id}` — Update address
* `DELETE /api/v1/addresses/{id}` — Delete address
* `PATCH  /api/v1/addresses/{id}/default` — Set as primary shipping address

### Coupons & Promotions (`/api/v1/coupons`)
* `GET    /api/v1/coupons` — List publicly available coupons
* `POST   /api/v1/coupons/validate` — Validate voucher code against cart subtotal
* `POST   /api/v1/admin/coupons` — Create coupon (`ADMIN`)
* `PUT    /api/v1/admin/coupons/{id}` — Update coupon details & quotas (`ADMIN`)
* `DELETE /api/v1/admin/coupons/{id}` — Deactivate / delete coupon (`ADMIN`)

### Orders & Checkout (`/api/v1/orders`)
* `POST   /api/v1/orders` — Place order from cart (Redis distributed lock & stock reservation)
* `GET    /api/v1/orders` — View customer's order history
* `GET    /api/v1/orders/{id}` — View order details and items
* `POST   /api/v1/orders/{id}/cancel` — Cancel order (releases reserved stock & coupon quota)

---

## Local Development Setup

### Prerequisites
* **Java**: JDK 21 (Eclipse Temurin, OpenJDK, or GraalVM)
* **Docker & Docker Compose**: v20.10+
* **Maven**: 3.9+ (or use the included `./mvnw` wrapper)

### 1. Start Infrastructure Services
Start PostgreSQL, Redis, Kafka, and SeaweedFS S3 containers in the background:
```bash
docker compose up -d
```

Verify that all four containers are healthy:
```bash
docker compose ps
```

| Service | Container Name | Port Mapping | Healthcheck / Endpoint |
| :--- | :--- | :--- | :--- |
| **PostgreSQL 17** | `ai-tech-marketplace-postgres` | `55433:5432` | `pg_isready -U ai_marketplace` |
| **SeaweedFS S3** | `ai-tech-marketplace-object-storage` | `8333:8333` | `http://localhost:8333` |
| **Redis 7** | `ai-tech-marketplace-redis` | `6379:6379` | `redis-cli ping` |
| **Apache Kafka 3.9** | `ai-tech-marketplace-kafka` | `9092:9092` | KRaft broker port 9092 |

### 2. Environment Variables & Configuration
Default configuration is located in [`src/main/resources/application.yaml`](file:///home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-backend/src/main/resources/application.yaml). You can override defaults using environment variables:

| Environment Variable | Default Value | Description |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:55433/ai_marketplace` | JDBC connection string |
| `SPRING_DATASOURCE_USERNAME` | `ai_marketplace` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `ai_marketplace_dev` | Database password |
| `REDIS_HOST` | `localhost` | Redis server hostname |
| `REDIS_PORT` | `6379` | Redis server port |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `S3_ENDPOINT` | `http://localhost:8333` | S3 API endpoint |
| `S3_ACCESS_KEY` | `admin` | S3 access key |
| `S3_SECRET_KEY` | `ai-marketplace-secret` | S3 secret key |
| `S3_BUCKET` | `products` | S3 bucket name |
| `JWT_SECRET` | *(development secret key)* | HMAC-SHA256 signature key |

### 3. Build & Run Application
Compile and start the Spring Boot application:
```bash
./mvnw clean spring-boot:run
```
The server will start listening at `http://localhost:8080`. Flyway will automatically execute any pending migrations on startup.

### 4. Running Tests
Run unit tests, integration tests, and Kafka/Redis lock verification suites:
```bash
./mvnw test
```

---

## Documentation & Architecture References

For detailed system requirements, database ERDs, API schemas, and architecture decision records, refer to:
* **[Documentation Repository (`ai-tech-marketplace-docs`)](../ai-tech-marketplace-docs)**
  * [System Architecture](../ai-tech-marketplace-docs/architecture/system-architecture.md)
  * [Database Design & Schema](../ai-tech-marketplace-docs/architecture/database-design.md)
  * [API Design Guide](../ai-tech-marketplace-docs/architecture/api-design.md)
  * [Development Roadmap](../ai-tech-marketplace-docs/development/roadmap.md)
