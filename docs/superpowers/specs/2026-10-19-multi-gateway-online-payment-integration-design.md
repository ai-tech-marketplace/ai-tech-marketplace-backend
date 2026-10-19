# Multi-Gateway Online Payment Integration Design Document

**Date:** 2026-10-19  
**Status:** Approved for Implementation Planning  
**Target Repository:** `ai-tech-marketplace-backend` & `ai-tech-marketplace-docs`  
**Authors:** DeepMind Antigravity Pair Programming Team

---

## 1. Intent & The Why

### 1.1 Context
Currently, the AI Tech Marketplace platform supports `COD` (Cash on Delivery) as its primary payment method. While COD covers customers preferring cash upon delivery, modern electronics and technology marketplaces require instant, secure, and automated digital payment gateways to:
- Guarantee seller cashflow and reduce delivery failure rates (RTO - Return to Origin).
- Support domestic digital banking & e-wallets in Vietnam via **VNPay** (ATM cards, VNPay-QR, internet banking) and **MoMo** (MoMo e-wallet, MoMo QR).
- Support international credit/debit cards (Visa, MasterCard, JCB) via **Stripe Checkout**.
- Automate order settlement and order lifecycle progression immediately upon receipt of asynchronous payment webhooks (IPN).

### 1.2 Success Criteria
- Seamless checkout initiation: calling `POST /api/v1/payments/{orderId}/initiate` generates and returns a secure payment redirect URL for the selected gateway (VNPay URL, MoMo App/Pay URL, or Stripe Checkout Session URL).
- Resilient asynchronous confirmation: backend securely handles IPN / Webhooks with cryptographic signature verification (VNPay HMAC-SHA512, MoMo HMAC-SHA256, Stripe Webhook Signature).
- Idempotency & Concurrency: duplicate webhook deliveries from gateways never double-credit settlements or create race conditions.
- Strict timeout & inventory rollback: online orders pending payment expire after 15 minutes if unpaid, releasing reserved product stock and coupon usage quotas.
- 100% test coverage with automated unit and integration tests using mocked gateway responses.

---

## 2. Goals, Non-Goals & Anti-Goals

### 2.1 Goals
- **Unified Provider Architecture**: Implement a clean `PaymentGatewayProvider` Strategy pattern allowing decoupled implementations for `VNPAY`, `MOMO`, `STRIPE`, and existing `COD`.
- **Zero-Decimal VND Currency**: All transactions processed uniformly in VND (`VND` is supported natively by VNPay, MoMo, and Stripe as a zero-decimal currency).
- **Cryptographic Security**: Verify digital signatures on all incoming IPN and webhook callbacks before modifying database state.
- **Auditable Payment Lifecycle**: Record comprehensive payment audit logs including `provider_transaction_id`, `provider_response`, `payment_method`, and precise timestamping.
- **Auto-Expiration & Stock Restock**: Background scheduled job to cancel orders remaining in `PENDING` payment status after 15 minutes, automatically restocking inventory and restoring coupon quotas.
- **Deterministic Late Payment Handling (Option A)**: In cases where a payment succeeds at the gateway after an order has already timed out (15 minutes), the gateway webhook is acknowledged with success (to halt retries), the order remains `CANCELLED`, payment is flagged `PAID_AFTER_CANCELLED`, and a dedicated customer refund alert is triggered.

### 2.2 Non-Goals
- Automated partial refund APIs via gateway merchant portals (refunds will initially be handled via admin reconciliation or future Phase 5 seller portal work).
- Physical recurring subscriptions or credit installment plans (handled as standard one-time checkout transactions).

### 2.3 Anti-Goals
- Trusting client-side return URLs for payment confirmation (Payment state transitions MUST only be executed via verified server-to-server IPN / Webhooks).
- Blocking database transactions while making external HTTP calls to gateway servers (External API requests must occur outside `@Transactional` blocks).
- Hardcoding credentials or URLs in code (all API keys, hashes, and endpoints must be configurable via Spring environment properties with sandbox defaults).

---

## 3. System Architecture & Component Design

### 3.1 Gateway Provider Strategy Pattern

```
                       +----------------------------------+
                       |          PaymentService          |
                       +-----------------+----------------+
                                         |
                       +-----------------v----------------+
                       |     PaymentProviderFactory       |
                       +-----------------+----------------+
                                         |
          +------------------------------+------------------------------+
          |                              |                              |
+---------v----------+         +---------v----------+         +---------v----------+
|   VNPayProvider    |         |    MoMoProvider    |         |   StripeProvider   |
+--------------------+         +--------------------+         +--------------------+
| - Build HMAC-SHA512|         | - Build HMAC-SHA256|         | - Create Stripe    |
|   redirect URL     |         |   Pay URL request  |         |   Checkout Session |
| - Verify IPN params|         | - Verify IPN sig   |         | - Verify Webhook   |
| - Return IPN resp  |         | - Return IPN resp  |         |   signature        |
+--------------------+         +--------------------+         +--------------------+
```

### 3.2 Core Interfaces & Data Contracts

```java
public interface PaymentGatewayProvider {
    PaymentMethod getSupportedMethod();
    PaymentInitiateResponse initiatePayment(Order order, Payment payment, String clientIp, String returnUrl);
    PaymentVerificationResult verifyCallback(Map<String, String> queryParams, String rawPayload, String signatureHeader);
}

public record PaymentInitiateResponse(
    String transactionReference,
    String paymentUrl,
    OffsetDateTime expiresAt
) {}

public record PaymentVerificationResult(
    boolean isValidSignature,
    boolean isSuccess,
    String transactionReference,
    String providerTransactionId,
    BigDecimal amount,
    String responseCode,
    String message,
    String rawResponse
) {}
```

---

## 4. Detailed Gateway Specifications

### 4.1 VNPay Integration (VND)
- **Unique Merchant Reference**: `vnp_TxnRef = {orderNumber}_{paymentId}_{timestamp}` (guarantees unique transaction references even if customer retries payment).
- **Initiation**: Generates query parameters sorted alphabetically by key name (`vnp_Amount`, `vnp_Command`, `vnp_CreateDate`, `vnp_CurrCode=VND`, `vnp_IpAddr`, `vnp_Locale=vn`, `vnp_OrderInfo`, `vnp_OrderType=other`, `vnp_ReturnUrl`, `vnp_TmnCode`, `vnp_TxnRef`, `vnp_Version=2.1.0`). Note that VNPay expects `amount * 100`. Calculates `vnp_SecureHash` using HMAC-SHA512.
- **IPN Handling (`GET /api/v1/payments/vnpay/ipn`)**:
  - Validates `vnp_SecureHash` against calculated checksum over incoming query params (excluding hash params).
  - Checks if order exists, verifies amount matches `order.totalAmount * 100`, checks if order is already paid.
  - Returns JSON response expected by VNPay (`{"RspCode":"00","Message":"Confirm Success"}`).
- **Return URL (`GET /api/v1/payments/vnpay/return`)**:
  - Validates checksum and issues HTTP 302 redirect back to storefront (`/checkout/result?orderNumber={orderNumber}&status=SUCCESS/FAILED`).

### 4.2 MoMo Integration (VND)
- **Unique Merchant Reference**: `orderId = {orderNumber}_{paymentId}_{timestamp}`.
- **Initiation**: Calls MoMo API (`POST /v2/gateway/api/create`) with:
  - `partnerCode`, `requestId`, `amount`, `orderId`, `orderInfo`, `redirectUrl`, `ipnUrl`, `requestType="captureWallet"`, `extraData`.
  - Signature computed via HMAC-SHA256: `accessKey=$accessKey&amount=$amount&extraData=$extraData&ipnUrl=$ipnUrl&orderId=$orderId&orderInfo=$orderInfo&partnerCode=$partnerCode&redirectUrl=$redirectUrl&requestId=$requestId&requestType=$requestType`.
  - Receives `payUrl` and `deeplink` to return to client.
- **IPN Handling (`POST /api/v1/payments/momo/ipn`)**:
  - Verifies signature using HMAC-SHA256.
  - If `resultCode == 0`, confirms payment as `PAID`.
  - Returns `{"resultCode": 0, "message": "Success"}` to MoMo server.

### 4.3 Stripe Integration (VND)
- **Dependency**: `com.stripe:stripe-java` (v28.x+).
- **Initiation**:
  - Creates a Stripe Checkout Session via Stripe API:
    - `payment_method_types: ["card"]`
    - `currency: "vnd"`
    - Line item amount equal to order total in VND (`amount = order.totalAmount.longValue()` since VND is zero-decimal).
    - `mode: "payment"`, `client_reference_id: payment.getTransactionReference()`.
    - `success_url` and `cancel_url`.
  - Returns `session.getUrl()`.
- **Webhook Handling (`POST /api/v1/payments/stripe/webhook`)**:
  - Verifies `Stripe-Signature` header using `Webhook.constructEvent(payload, sigHeader, webhookSecret)`.
  - Authoritative event: handles `checkout.session.completed`. Extracts `client_reference_id` and `payment_intent`.
  - Updates payment to `PAID`. Returns HTTP 200 `{"received": true}`.

---

## 5. Database Schema & State Machine

### 5.1 Enums & Entity Enhancements
- `PaymentMethod`: `COD`, `VNPAY`, `MOMO`, `STRIPE`.
- `PaymentStatus`: `PENDING`, `PAID`, `FAILED`, `CANCELLED`, `PAID_AFTER_CANCELLED`.
- `Payment` Entity updates:
  - `transactionReference`: `VARCHAR(100)` (Unique merchant transaction identifier).
  - `provider`: `VARCHAR(50)` (e.g., `VNPAY`, `MOMO`, `STRIPE`).
  - `providerTransactionId`: `VARCHAR(100)` (external transaction ID from gateway).
  - `providerResponse`: `TEXT` (JSON representation of IPN/Webhook confirmation payload).
  - `paidAt`: `TIMESTAMP WITH TIME ZONE`.
  - `expiresAt`: `TIMESTAMP WITH TIME ZONE`.

### 5.2 Flyway Migration `V12__enhance_payments_and_online_gateways.sql`
- Add columns to `payments` table:
  - `transaction_reference VARCHAR(100) UNIQUE`
  - `provider_transaction_id VARCHAR(100)`
  - `provider_response TEXT`
  - `paid_at TIMESTAMP WITH TIME ZONE`
  - `expires_at TIMESTAMP WITH TIME ZONE`
- Add performance indexes:
  - `CREATE UNIQUE INDEX idx_payments_transaction_reference ON payments(transaction_reference);`
  - `CREATE INDEX idx_payments_provider_txn ON payments(provider_transaction_id);`
  - `CREATE INDEX idx_orders_pending_online_payment ON orders(status, payment_status, created_at) WHERE status = 'PENDING';`

### 5.3 Order & Settlement State Transition Rules
- **Successful Online Payment**:
  - `Payment.status = PAID`, `Payment.paidAt = NOW()`.
  - `Order.paymentStatus = PAID`, `Order.status = CONFIRMED`.
  - `SellerOrderSettlement.status`: Remains `PENDING` in escrow until order transitions to `DELIVERED` (preserves marketplace escrow protection rules).
  - Publishes `OrderPaidEvent` to Apache Kafka topic `marketplace.orders`.
- **Late Payment after 15-Minute Cancellation (Option A Decision)**:
  - `Payment.status = PAID_AFTER_CANCELLED`.
  - `Order.status` remains `CANCELLED` (no stock re-reservation to prevent overselling).
  - Gateway is acknowledged with Success response (preventing webhook retry storms).
  - Logs high-priority alert for Customer Support / Refund processing.

---

## 6. Order Expiration & Stock Rollback Scheduler

- Scheduled task running every 1 minute (`@Scheduled(fixedRate = 60000)`):
  - Queries orders where `payment_status = 'PENDING'`, `status = 'PENDING'`, `payment_method != 'COD'`, and `created_at < NOW() - INTERVAL '15 minutes'`.
  - For each expired order within an atomic transaction:
    - Updates order status to `CANCELLED`.
    - Updates payment status to `CANCELLED`.
    - Restocks product inventory (`stockQuantity = stockQuantity + item.quantity`).
    - Releases coupon usage quota if applicable (`couponUsage.status = 'RELEASED'`).
    - Publishes `OrderCancelledEvent` to Apache Kafka topic `marketplace.orders`.

---

## 7. Security & Failure Scenarios

| Scenario | Risk | Mitigation |
| :--- | :--- | :--- |
| Tampered IPN checksum | Fraudulent order confirmation | Cryptographic HMAC-SHA512/HMAC-SHA256 and Stripe-Signature header verification. Reject on signature mismatch. |
| Duplicate Webhooks | Duplicate processing or race conditions | Idempotency guard: checks if `payment.status == PAID`. If so, logs and returns HTTP 200 / RspCode "00" immediately without re-processing. |
| Amount Mismatch | Underpayment exploit | Strict verification that `ipnAmount == order.totalAmount`. If mismatched, marks payment `FAILED` and halts order progression. |
| Late Payment after 15m Expiration | Unintended charge on cancelled order | Marks payment `PAID_AFTER_CANCELLED`, keeps order `CANCELLED`, acknowledges gateway to avoid retries, alerts CS for refund. |
| Malicious Open Redirect | Phishing via redirect URL | Validate return URL against whitelist configured in `application.yaml` (`app.cors.allowed-origins`). |
