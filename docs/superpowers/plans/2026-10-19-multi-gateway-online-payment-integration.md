# Multi-Gateway Online Payment Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a production-grade multi-gateway online payment system supporting VNPay, MoMo, and Stripe in VND, with cryptographic signature verification, idempotent webhook processing, and an automated 15-minute order expiration scheduler.

**Architecture:** A decoupled Strategy Pattern via `PaymentGatewayProvider` with dedicated implementations for `VNPayProvider` (HMAC-SHA512), `MoMoProvider` (HMAC-SHA256), and `StripeProvider` (Stripe Checkout & Webhook Signature). A unified `PaymentService` orchestrates checkout initiation, server-to-server IPN confirmation, and a Spring `@Scheduled` background worker that cancels unpaid orders after 15 minutes, restocking inventory and releasing coupon quotas.

**Tech Stack:** Java 21, Spring Boot 4.1.1, PostgreSQL 17, Spring Data JPA, Flyway (V12), Apache Kafka (KRaft), `com.stripe:stripe-java` (28.x), JUnit 5, Mockito, Spring WebMvc MockMvc.

**Spec:** [`docs/superpowers/specs/2026-10-19-multi-gateway-online-payment-integration-design.md`](file:///home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-backend/docs/superpowers/specs/2026-10-19-multi-gateway-online-payment-integration-design.md)

---

## Global Constraints

- Currency is strictly `VND` across all providers; Stripe amounts are formatted as zero-decimal (`amount = order.totalAmount.longValue()`).
- Server-to-server IPN/Webhooks are the sole authority for transitioning `order.paymentStatus` to `PAID` (client return URLs must NEVER mark orders paid).
- In accordance with Decision A, late payments arriving after an order has expired (15 minutes) are acknowledged to the gateway, the order remains `CANCELLED`, payment is flagged `PAID_AFTER_CANCELLED`, and an admin refund alert is logged.
- Merchant transaction references (`transactionReference`) must follow `{orderNumber}_{paymentId}_{timestamp}` to prevent duplicate transaction errors at VNPay/MoMo during customer payment retries.
- Webhook endpoints must be completely idempotent: duplicate IPN requests for an already `PAID` transaction return success immediately without side effects.
- External HTTP requests to payment gateways must execute outside of database transaction boundaries.

---

## Review Focus

1. **Checksum Tampering**: An attacker alters the IPN amount, transaction ID, or status; the system must reject the callback due to signature mismatch and not alter order or payment state.
2. **Duplicate Webhook Delivery**: Cổng thanh toán sends the same IPN callback multiple times concurrently; the system must process the first one atomically and return success on subsequent deliveries without duplicate events or settlement corruption.
3. **Late Payment on Expired Order**: Customer pays at gateway after the 15-minute timeout has cancelled the order; system must acknowledge gateway with success ("00" / HTTP 200), set payment to `PAID_AFTER_CANCELLED`, keep order `CANCELLED`, and not oversell inventory.
4. **Amount Mismatch**: Gateway webhook reports an amount different from the order total; system must reject the transaction, mark payment `FAILED`, and log a security alert.
5. **Customer Payment Method Switching / Retries**: A customer initiates payment with VNPay, abandons it, and initiates again with MoMo; system must generate distinct `transactionReference` values so the second gateway accepts the transaction.

---

### Task 1: Flyway Migration V12 & Payment Entity/Enum Extensions

**Files:**
- Create: `src/main/resources/db/migration/V12__enhance_payments_and_online_gateways.sql`
- Modify: `src/main/java/com/aitechmarketplace/backend/order/entity/PaymentMethod.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/order/entity/PaymentStatus.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/order/entity/Payment.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/order/repository/PaymentRepository.java`
- Test: `src/test/java/com/aitechmarketplace/backend/payment/repository/PaymentRepositoryTest.java`

**Interfaces:**
- Produces:
  - `PaymentMethod`: `COD`, `VNPAY`, `MOMO`, `STRIPE`
  - `PaymentStatus`: `PENDING`, `PAID`, `FAILED`, `CANCELLED`, `PAID_AFTER_CANCELLED`
  - `Payment`: fields `transactionReference`, `provider`, `providerTransactionId`, `providerResponse`, `paidAt`, `expiresAt`
  - `PaymentRepository`:
    - `Optional<Payment> findByTransactionReference(String transactionReference)`
    - `Optional<Payment> findTopByOrderIdOrderByCreatedAtDesc(Long orderId)`

- [ ] **Step 1: Write `V12__enhance_payments_and_online_gateways.sql`**
  Add columns `transaction_reference VARCHAR(100) UNIQUE`, `provider VARCHAR(50)`, `provider_transaction_id VARCHAR(100)`, `provider_response TEXT`, `paid_at TIMESTAMP WITH TIME ZONE`, `expires_at TIMESTAMP WITH TIME ZONE`. Add indexes on `transaction_reference` and `orders(status, payment_status, created_at)`.
- [ ] **Step 2: Update Enums and `Payment` entity**
  Add `VNPAY`, `MOMO`, `STRIPE` to `PaymentMethod`. Add `PAID_AFTER_CANCELLED` to `PaymentStatus`. Add new fields and getters/setters in `Payment.java`.
- [ ] **Step 3: Update `PaymentRepository`**
  Add query methods `findByTransactionReference` and `findTopByOrderIdOrderByCreatedAtDesc`.
- [ ] **Step 4: Write repository tests**
  Write tests in `PaymentRepositoryTest` verifying persistence of new fields, retrieval by `transactionReference`, and enum mapping.
- [ ] **Step 5: Run repository tests**
  Run: `./mvnw test -Dtest=PaymentRepositoryTest`
  Expected: PASS
- [ ] **Step 6: Commit**
  ```bash
  git add src/main/resources/db/migration/V12__enhance_payments_and_online_gateways.sql src/main/java/com/aitechmarketplace/backend/order/ src/test/java/com/aitechmarketplace/backend/payment/
  git commit -m "feat(payment): add Flyway V12 migration and extend Payment entity for online gateways"
  ```

---

### Task 2: Payment Provider Strategy Core & VNPay Gateway Implementation

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/payment/provider/PaymentGatewayProvider.java`
- Create: `src/main/java/com/aitechmarketplace/backend/payment/dto/PaymentInitiateResponse.java`
- Create: `src/main/java/com/aitechmarketplace/backend/payment/dto/PaymentVerificationResult.java`
- Create: `src/main/java/com/aitechmarketplace/backend/payment/config/VnpayProperties.java`
- Create: `src/main/java/com/aitechmarketplace/backend/payment/provider/vnpay/VnpayProvider.java`
- Create: `src/main/java/com/aitechmarketplace/backend/payment/provider/vnpay/VnpayUtils.java`
- Test: `src/test/java/com/aitechmarketplace/backend/payment/provider/VnpayProviderTest.java`

**Interfaces:**
- Produces:
  - `PaymentGatewayProvider`:
    - `PaymentMethod getSupportedMethod()`
    - `PaymentInitiateResponse initiatePayment(Order order, Payment payment, String clientIp, String returnUrl)`
    - `PaymentVerificationResult verifyCallback(Map<String, String> queryParams, String rawPayload, String signatureHeader)`
- Produces:
  - `VnpayProvider` implementing `PaymentGatewayProvider` for `PaymentMethod.VNPAY`. Calculates HMAC-SHA512 checksum over alphabetically sorted query params.

- [ ] **Step 1: Define Core Provider Interfaces and DTOs**
  Create `PaymentGatewayProvider`, `PaymentInitiateResponse`, and `PaymentVerificationResult`.
- [ ] **Step 2: Create `VnpayProperties` and `VnpayUtils`**
  Implement HMAC-SHA512 hashing utility and query string builder with URL encoding.
- [ ] **Step 3: Write unit tests for `VnpayProvider`**
  Write `VnpayProviderTest`:
  - Test payment URL generation includes `vnp_SecureHash`, `vnp_Amount` (order total * 100), and `vnp_TxnRef`.
  - Test valid IPN callback checksum verification returns `isSuccess = true`.
  - Test tampered IPN callback checksum verification returns `isValidSignature = false`.
- [ ] **Step 4: Implement `VnpayProvider`**
  Implement `initiatePayment` and `verifyCallback` conforming to VNPay 2.1.0 specifications.
- [ ] **Step 5: Run tests**
  Run: `./mvnw test -Dtest=VnpayProviderTest`
  Expected: PASS
- [ ] **Step 6: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/payment/ src/test/java/com/aitechmarketplace/backend/payment/
  git commit -m "feat(payment): implement payment provider strategy core and VNPay provider"
  ```

---

### Task 3: MoMo Gateway Provider Implementation

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/payment/config/MomoProperties.java`
- Create: `src/main/java/com/aitechmarketplace/backend/payment/provider/momo/MomoProvider.java`
- Create: `src/main/java/com/aitechmarketplace/backend/payment/provider/momo/MomoUtils.java`
- Create: `src/main/java/com/aitechmarketplace/backend/payment/dto/momo/MomoCreatePaymentRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/payment/dto/momo/MomoCreatePaymentResponse.java`
- Test: `src/test/java/com/aitechmarketplace/backend/payment/provider/MomoProviderTest.java`

**Interfaces:**
- Produces:
  - `MomoProvider` implementing `PaymentGatewayProvider` for `PaymentMethod.MOMO`. Calculates HMAC-SHA256 signature, initiates payment request to MoMo gateway, and verifies incoming IPN signature and `resultCode == 0`.

- [ ] **Step 1: Create `MomoProperties`, DTOs and `MomoUtils`**
  Create properties for partnerCode, accessKey, secretKey, endpoint, and HMAC-SHA256 signature generator.
- [ ] **Step 2: Write unit tests for `MomoProvider`**
  Write `MomoProviderTest`:
  - Test initiation signature creation.
  - Test verification of valid MoMo IPN callback (`resultCode == 0` -> `isSuccess = true`).
  - Test verification of failed MoMo IPN callback (`resultCode != 0` -> `isSuccess = false`).
  - Test signature mismatch rejects callback.
- [ ] **Step 3: Implement `MomoProvider`**
  Inject `RestClient` or `RestTemplate` to send create payment request to MoMo API; handle response and extract `payUrl`. Verify IPN payload.
- [ ] **Step 4: Run tests**
  Run: `./mvnw test -Dtest=MomoProviderTest`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/payment/ src/test/java/com/aitechmarketplace/backend/payment/
  git commit -m "feat(payment): implement MoMo payment provider with HMAC-SHA256 verification"
  ```

---

### Task 4: Stripe Gateway Provider Implementation

**Files:**
- Modify: `pom.xml` (Add `com.stripe:stripe-java:28.1.0`)
- Create: `src/main/java/com/aitechmarketplace/backend/payment/config/StripeProperties.java`
- Create: `src/main/java/com/aitechmarketplace/backend/payment/provider/stripe/StripeProvider.java`
- Test: `src/test/java/com/aitechmarketplace/backend/payment/provider/StripeProviderTest.java`

**Interfaces:**
- Produces:
  - `StripeProvider` implementing `PaymentGatewayProvider` for `PaymentMethod.STRIPE`.
  - Creates Stripe Checkout Session in `vnd` zero-decimal format (`amount = totalAmount.longValue()`).
  - Verifies incoming webhook with `Webhook.constructEvent` and handles `checkout.session.completed`.

- [ ] **Step 1: Add `stripe-java` dependency in `pom.xml`**
  Add `software.amazon.awssdk` / standard dependencies, add `com.stripe:stripe-java`.
- [ ] **Step 2: Create `StripeProperties`**
  Define `apiKey`, `webhookSecret`, `successUrl`, `cancelUrl`.
- [ ] **Step 3: Write unit tests for `StripeProvider`**
  Write `StripeProviderTest`:
  - Test session creation parameters: currency is `vnd`, amount matches order total without decimals, `client_reference_id` set to `transactionReference`.
  - Test webhook signature validation using mock payload and test secret.
- [ ] **Step 4: Implement `StripeProvider`**
  Implement `initiatePayment` using Stripe SDK `Session.create(...)`. Implement `verifyCallback` using `Webhook.constructEvent`.
- [ ] **Step 5: Run tests**
  Run: `./mvnw test -Dtest=StripeProviderTest`
  Expected: PASS
- [ ] **Step 6: Commit**
  ```bash
  git add pom.xml src/main/java/com/aitechmarketplace/backend/payment/ src/test/java/com/aitechmarketplace/backend/payment/
  git commit -m "feat(payment): implement Stripe Checkout provider for VND zero-decimal payments"
  ```

---

### Task 5: Payment Service, Factory & Webhook Controller Orchestration

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/payment/provider/PaymentProviderFactory.java`
- Create: `src/main/java/com/aitechmarketplace/backend/payment/dto/PaymentInitiateRequest.java`
- Create: `src/main/java/com/aitechmarketplace/backend/payment/service/PaymentService.java`
- Create: `src/main/java/com/aitechmarketplace/backend/payment/service/PaymentServiceImpl.java`
- Create: `src/main/java/com/aitechmarketplace/backend/payment/controller/PaymentController.java`
- Create: `src/main/java/com/aitechmarketplace/backend/order/event/OrderPaidEvent.java`
- Test: `src/test/java/com/aitechmarketplace/backend/payment/service/PaymentServiceImplTest.java`
- Test: `src/test/java/com/aitechmarketplace/backend/payment/controller/PaymentControllerIntegrationTest.java`

**Interfaces:**
- Produces:
  - `PaymentService`:
    - `PaymentInitiateResponse initiatePayment(Long userId, Long orderId, PaymentInitiateRequest request, String clientIp)`
    - `Map<String, Object> handleVnpayIpn(Map<String, String> queryParams)`
    - `Map<String, Object> handleMomoIpn(Map<String, String> queryParams, String rawPayload)`
    - `Map<String, Object> handleStripeWebhook(String rawPayload, String signatureHeader)`
- Endpoints:
  - `POST /api/v1/payments/{orderId}/initiate` (`@PreAuthorize("isAuthenticated()")`)
  - `GET /api/v1/payments/vnpay/ipn` (Public)
  - `GET /api/v1/payments/vnpay/return` (Public, 302 redirect to storefront)
  - `POST /api/v1/payments/momo/ipn` (Public)
  - `GET /api/v1/payments/momo/return` (Public, 302 redirect)
  - `POST /api/v1/payments/stripe/webhook` (Public)
  - `GET /api/v1/payments/stripe/return` (Public, 302 redirect)

- [ ] **Step 1: Create `OrderPaidEvent` and DTOs**
  Define `OrderPaidEvent(Long orderId, String orderNumber, Long userId, BigDecimal totalAmount, PaymentMethod paymentMethod, String providerTransactionId, OffsetDateTime paidAt)`.
- [ ] **Step 2: Implement `PaymentProviderFactory`**
  Inject list of `PaymentGatewayProvider` beans and index by `PaymentMethod`.
- [ ] **Step 3: Write unit tests for `PaymentServiceImpl`**
  Cover:
  - Valid initiation creates payment record with unique `transactionReference` and returns URL.
  - Initiation on cancelled/already paid order throws `BadRequestException`.
  - Non-owner initiation throws `ForbiddenException`.
  - Valid IPN transitions order to `CONFIRMED` and `PAID`, publishes `OrderPaidEvent`.
  - Duplicate IPN returns success idempotently without publishing duplicate event.
  - Late IPN on `CANCELLED` order sets payment to `PAID_AFTER_CANCELLED`, logs alert, does NOT alter `order.status`.
  - Amount mismatch marks payment `FAILED`.
- [ ] **Step 4: Implement `PaymentServiceImpl`**
  Implement transaction boundaries, database locking, status transitions, and Kafka event publishing.
- [ ] **Step 5: Implement `PaymentController`**
  Expose initiation, IPN, and return redirect endpoints.
- [ ] **Step 6: Write integration test `PaymentControllerIntegrationTest`**
  Test end-to-end HTTP calls: initiation, VNPay IPN, MoMo IPN, and Stripe webhook.
- [ ] **Step 7: Run integration tests**
  Run: `./mvnw test -Dtest=PaymentControllerIntegrationTest,PaymentServiceImplTest`
  Expected: PASS
- [ ] **Step 8: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/payment/ src/main/java/com/aitechmarketplace/backend/order/event/ src/test/java/com/aitechmarketplace/backend/payment/
  git commit -m "feat(payment): implement PaymentService, provider factory, and webhook controller"
  ```

---

### Task 6: 15-Minute Unpaid Order Auto-Expiration Scheduler

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/order/scheduler/OrderExpirationScheduler.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/BackendApplication.java` (Ensure `@EnableScheduling`)
- Modify: `src/main/java/com/aitechmarketplace/backend/order/repository/OrderRepository.java`
- Test: `src/test/java/com/aitechmarketplace/backend/order/scheduler/OrderExpirationSchedulerTest.java`

**Interfaces:**
- Produces:
  - `OrderRepository.findExpiredPendingOrders(OffsetDateTime cutoffTime)`
  - `OrderExpirationScheduler.cancelExpiredOrders()` running every 60 seconds (`@Scheduled(fixedRate = 60000)`).
  - Automatically restores product inventory stock and sets `couponUsage.status = RELEASED`.

- [ ] **Step 1: Add query to `OrderRepository`**
  Query orders where `status = 'PENDING'`, `paymentStatus = 'PENDING'`, `paymentMethod != 'COD'`, and `createdAt < :cutoffTime`.
- [ ] **Step 2: Write unit/integration tests for `OrderExpirationScheduler`**
  Test:
  - Expired pending order is cancelled after 15 minutes.
  - Product stock is restored by item quantity.
  - Coupon quota is released (`CouponUsageStatus.RELEASED`).
  - `OrderCancelledEvent` is published to Kafka.
  - Orders under 15 minutes or COD orders are untouched.
- [ ] **Step 3: Implement `OrderExpirationScheduler`**
  Implement scheduled method with `@Transactional` per order or batch iteration.
- [ ] **Step 4: Run tests**
  Run: `./mvnw test -Dtest=OrderExpirationSchedulerTest`
  Expected: PASS
- [ ] **Step 5: Commit**
  ```bash
  git add src/main/java/com/aitechmarketplace/backend/order/ src/test/java/com/aitechmarketplace/backend/order/
  git commit -m "feat(order): implement 15-minute unpaid order expiration scheduler with inventory and coupon rollback"
  ```

---

### Task 7: Documentation Synchronization & Full Project Verification

**Files:**
- Create/Modify: `ai-tech-marketplace-docs/api/payments.md`
- Modify: `ai-tech-marketplace-docs/database/schema.md`
- Modify: `ai-tech-marketplace-docs/database/migrations.md`
- Modify: `ai-tech-marketplace-docs/requirements/business-rules.md`
- Modify: `ai-tech-marketplace-docs/development/roadmap.md`
- Modify: `ai-tech-marketplace-docs/README.md`
- Modify: `ai-tech-marketplace-backend/README.md`
- Test: Run full Maven test suite

- [ ] **Step 1: Run full Maven test suite**
  Run: `./mvnw clean test`
  Expected: BUILD SUCCESS with 100% pass across all modules.
- [ ] **Step 2: Update documentation in `ai-tech-marketplace-docs`**
  Document payment contracts, V12 schema, business rules (`BR-PAYMENT-001` to `BR-PAYMENT-005`), and roadmap status.
- [ ] **Step 3: Update `ai-tech-marketplace-backend/README.md`**
  Add V12 to Flyway table and payment endpoints to the API routes overview.
- [ ] **Step 4: Commit documentation in both repos**
  ```bash
  git add ... && git commit -m "docs: document multi-gateway online payment system and Flyway V12"
  ```
