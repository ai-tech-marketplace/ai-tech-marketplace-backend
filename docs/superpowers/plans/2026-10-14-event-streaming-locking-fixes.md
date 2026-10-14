# Event Streaming & Locking Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix two post-review issues on branch `feature/event-streaming-locking`: (1) the distributed lock must wrap the Spring transaction, not be inside it; (2) Kafka partition key must be `userId` (user-level ordering) instead of `orderNumber` (order-level ordering).

**Architecture:**
- Task 1 extracts the transactional checkout body into a new Spring `@Service` bean (`CheckoutTransactionService`) so the `@Transactional` AOP proxy wraps *only* the inner method. `OrderServiceImpl.createOrder()` loses its `@Transactional`, acquires the Redis lock, delegates to `CheckoutTransactionService.execute()`, then releases the lock in `finally` — guaranteeing the transaction commits *before* the lock is released.
- Task 2 is a one-line change per `publish*` method in `DomainEventPublisher`, switching the Kafka record key from `event.orderNumber()` to `event.userId().toString()`, plus matching test assertions.

**Tech Stack:** Spring Boot 4.1.1, Spring Integration Redis (`RedisLockRegistry`), Spring Kafka (`KafkaTemplate`), JUnit 5, Mockito 5 (`@MockitoBean`/`@MockitoSpyBean`), EmbeddedKafka, Redis (localhost:6379).

**Spec:** `docs/superpowers/plans/2026-10-14-event-streaming-locking-fixes.md` (this file; original review findings in commit history).

## Global Constraints

- Spring Boot **4.1.1** — use `@MockitoBean` (not `@MockBean`), `@MockitoSpyBean` (not `@SpyBean`).
- `@Transactional` on a `private` method is silently ignored by Spring AOP → all transactional methods must be on a separate Spring bean (not self-invoked).
- Branch: `feature/event-streaming-locking` — do NOT commit to `develop`.
- All 220 existing tests must stay green after every commit.
- Follow TDD: write failing test → confirm RED → implement → confirm GREEN → commit.

## Review Focus

- Lock released while TX still open → another thread sees a half-committed order — covered by Task 1, Step 1–4 (verify lock `unlock()` is called *after* the `@Transactional` method returns, not before).
- `InterruptedException` path resets interrupt flag but callers may swallow it → Task 1 keeps `Thread.currentThread().interrupt()` + throws `ConflictException`.
- Kafka send is fire-and-forget: `publishOrderCreated` does not block — integration test asserts the message lands within 10 s; no new concern, already covered by `DomainEventPublisherTest`.
- `userId`-based partition key skews all events for a power user to one partition → acceptable trade-off for user-level ordering; document in log message (covered in Task 2 Step 3).
- `CheckoutTransactionService` is a new bean: if it is not found by the test context (missing component scan) tests will fail with `NoSuchBeanDefinitionException` — same package as `OrderServiceImpl`, so `@SpringBootTest` picks it up automatically.

---

## Task 1: Fix Distributed Lock Transaction Boundary

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/order/service/CheckoutTransactionService.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/order/service/OrderServiceImpl.java` (lines 88–208)
- Test (new): `src/test/java/com/aitechmarketplace/backend/order/service/CheckoutLockBoundaryTest.java`
- Test (update): `src/test/java/com/aitechmarketplace/backend/order/service/OrderServiceDistributedLockTest.java`
- Test (update): `src/test/java/com/aitechmarketplace/backend/order/service/OrderServiceImplTest.java`

**Interfaces:**
- Produces: `CheckoutTransactionService.execute(Long userId, OrderCreateRequest request): OrderResponse` — `@Transactional`, `@Service`, package `com.aitechmarketplace.backend.order.service`.
- `OrderServiceImpl.createOrder()` — removes `@Transactional`; injects `CheckoutTransactionService`; acquires lock → calls `checkoutTransactionService.execute(userId, request)` → releases lock in `finally`.

---

- [ ] **Step 1: Write the failing test** in `CheckoutLockBoundaryTest.java`

```java
package com.aitechmarketplace.backend.order.service;

import com.aitechmarketplace.backend.order.dto.OrderCreateRequest;
import com.aitechmarketplace.backend.order.dto.OrderResponse;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.integration.support.locks.DistributedLock;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
class CheckoutLockBoundaryTest {

    @Autowired
    private OrderService orderService;

    @MockitoBean(name = "checkoutLockRegistry")
    private RedisLockRegistry checkoutLockRegistry;

    @MockitoBean
    private CheckoutTransactionService checkoutTransactionService;

    @Test
    void createOrder_releasesLockAfterTransactionCommits() throws Exception {
        // Arrange
        DistributedLock mockLock = mock(DistributedLock.class);
        when(checkoutLockRegistry.obtain(anyString())).thenReturn(mockLock);
        when(mockLock.tryLock(5, TimeUnit.SECONDS)).thenReturn(true);

        AtomicBoolean txActiveWhenExecuteCalled = new AtomicBoolean(false);
        when(checkoutTransactionService.execute(anyLong(), any())).thenAnswer(inv -> {
            txActiveWhenExecuteCalled.set(TransactionSynchronizationManager.isActualTransactionActive());
            return mock(OrderResponse.class);
        });

        // Act
        orderService.createOrder(1L, new OrderCreateRequest(10L, PaymentMethod.COD, null, null));

        // Assert: lock was unlocked exactly once
        verify(mockLock, times(1)).unlock();
        // Assert: outer createOrder() has NO active transaction (lock is outside TX)
        assertThat(txActiveWhenExecuteCalled.get()).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

  Run: `./mvnw test -pl . -Dtest=CheckoutLockBoundaryTest -q 2>&1 | tail -20`
  Expected: FAIL — `CheckoutTransactionService` bean not found OR test assertion fails because `@Transactional` is still on `createOrder()`.

- [ ] **Step 3: Create `CheckoutTransactionService.java`**

  Move the entire body of `OrderServiceImpl.createOrder()` (lines 103–207, the `try` block after lock acquisition) into `CheckoutTransactionService.execute(Long userId, OrderCreateRequest request): OrderResponse`.
  The new class receives the same constructor dependencies as `OrderServiceImpl` minus `checkoutLockRegistry`.
  Annotate the class `@Service` and the method `@Transactional`.

  Signature:
  ```java
  @Service
  public class CheckoutTransactionService {
      // same repository/service fields as OrderServiceImpl (minus checkoutLockRegistry)
      @Transactional
      public OrderResponse execute(Long userId, OrderCreateRequest request) { ... }
  }
  ```

- [ ] **Step 4: Modify `OrderServiceImpl.createOrder()`**

  - Remove `@Transactional` from `createOrder()`.
  - Add `CheckoutTransactionService checkoutTransactionService` field + constructor parameter.
  - Replace the old `try { ...business logic... } finally { lock.unlock(); }` block with:
    ```java
    try {
        return checkoutTransactionService.execute(userId, request);
    } finally {
        lock.unlock();
    }
    ```
  - Remove all repository/service imports/fields that are now only used in `CheckoutTransactionService` (keep only `checkoutLockRegistry` and `checkoutTransactionService`).

- [ ] **Step 5: Update `OrderServiceDistributedLockTest`**

  The test still mocks `checkoutLockRegistry` and calls `orderService.createOrder()`. It should continue to pass unchanged. Verify by running the test — no source changes needed unless compilation fails.

- [ ] **Step 6: Update `OrderServiceImplTest`**

  `OrderServiceImplTest` currently mocks all repositories/services injected into `OrderServiceImpl`. After the refactor those dependencies live in `CheckoutTransactionService`, so:
  - Add `@Mock CheckoutTransactionService checkoutTransactionService` to the test.
  - Remove `@Mock` annotations for dependencies moved to `CheckoutTransactionService` (they are no longer injected into `OrderServiceImpl`).
  - Update `@InjectMocks OrderServiceImpl` constructor call or field injection accordingly.
  - If existing unit tests previously exercised checkout business logic through `OrderServiceImpl`, replace them with a stub: `when(checkoutTransactionService.execute(...)).thenReturn(mockResponse)`.

- [ ] **Step 7: Run the target test to verify it passes**

  Run: `./mvnw test -pl . -Dtest=CheckoutLockBoundaryTest -q 2>&1 | tail -20`
  Expected: PASS

- [ ] **Step 8: Run the full test suite**

  Run: `./mvnw test -q 2>&1 | tail -30`
  Expected: BUILD SUCCESS, all tests pass (≥ 220).

- [ ] **Step 9: Commit**

  ```bash
  git add src/main/java/com/aitechmarketplace/backend/order/service/CheckoutTransactionService.java \
          src/main/java/com/aitechmarketplace/backend/order/service/OrderServiceImpl.java \
          src/test/java/com/aitechmarketplace/backend/order/service/CheckoutLockBoundaryTest.java \
          src/test/java/com/aitechmarketplace/backend/order/service/OrderServiceDistributedLockTest.java \
          src/test/java/com/aitechmarketplace/backend/order/service/OrderServiceImplTest.java
  git commit -m "fix(order): move Redis lock outside @Transactional boundary via CheckoutTransactionService"
  ```

---

## Task 2: Switch Kafka Partition Key to userId

**Files:**
- Modify: `src/main/java/com/aitechmarketplace/backend/event/DomainEventPublisher.java` (lines 21, 34)
- Modify: `src/test/java/com/aitechmarketplace/backend/event/DomainEventPublisherTest.java`

**Interfaces:**
- Consumes: `OrderCreatedEvent.userId(): Long`, `OrderCancelledEvent.userId(): Long` (already exist).
- No change to public method signatures.

---

- [ ] **Step 1: Write the failing test**

  In `DomainEventPublisherTest`, add assertions that the Kafka record key equals `userId.toString()`. Update **both** existing test methods:

  ```java
  // In publishOrderCreated_sendsToKafka():
  var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
  assertThat(records.count()).isGreaterThanOrEqualTo(1);
  records.forEach(record -> assertThat(record.key()).isEqualTo("1")); // userId=1L

  // In publishOrderCancelled_sendsToKafka():
  var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
  assertThat(records.count()).isGreaterThanOrEqualTo(1);
  records.forEach(record -> assertThat(record.key()).isEqualTo("1")); // userId=1L
  ```

- [ ] **Step 2: Run test to verify it fails**

  Run: `./mvnw test -pl . -Dtest=DomainEventPublisherTest -q 2>&1 | tail -20`
  Expected: FAIL — key is `"ORD-TEST-001"` / `"ORD-TEST-002"` instead of `"1"`.

- [ ] **Step 3: Update `DomainEventPublisher`**

  Change the key argument in both `kafkaTemplate.send()` calls:

  - `publishOrderCreated`: `event.orderNumber()` → `event.userId().toString()`
  - `publishOrderCancelled`: `event.orderNumber()` → `event.userId().toString()`

  Also update the log messages to include `userId` alongside `orderNumber` for traceability:
  ```java
  log.info("Publishing OrderCreatedEvent for order: {}, userId: {}", event.orderNumber(), event.userId());
  ```

- [ ] **Step 4: Run the target test to verify it passes**

  Run: `./mvnw test -pl . -Dtest=DomainEventPublisherTest -q 2>&1 | tail -20`
  Expected: PASS

- [ ] **Step 5: Run the full test suite**

  Run: `./mvnw test -q 2>&1 | tail -30`
  Expected: BUILD SUCCESS, all tests pass (≥ 220).

- [ ] **Step 6: Commit**

  ```bash
  git add src/main/java/com/aitechmarketplace/backend/event/DomainEventPublisher.java \
          src/test/java/com/aitechmarketplace/backend/event/DomainEventPublisherTest.java
  git commit -m "fix(event): use userId as Kafka partition key for user-level message ordering"
  ```
