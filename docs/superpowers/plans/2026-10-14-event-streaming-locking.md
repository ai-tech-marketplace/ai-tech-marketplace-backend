# Event Streaming & Distributed Locking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Apache Kafka event streaming and Redis distributed locking to the checkout pipeline, enabling async domain-event consumers and preventing concurrent double-submit race conditions.

**Architecture:** Redis provides distributed locks on `checkout:user:{userId}` to serialize checkout requests per user and acts as a future caching layer. Kafka publishes domain events (`OrderCreated`, `OrderCancelled`) after successful transactions, consumed by notification and analytics listeners. Pessimistic row-level locking (`SELECT ... FOR UPDATE`) on product stock rows prevents overselling during concurrent checkouts for different users targeting the same product.

**Tech Stack:** Spring Boot 4.1.1, Java 21, Spring Data Redis (Lettuce), Spring Integration Redis (`RedisLockRegistry`), Spring Kafka, Apache Kafka (KRaft mode — no Zookeeper), PostgreSQL 17, Docker Compose.

**Spec:**
- [`architecture-decisions.md`](file:///home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/architecture/architecture-decisions.md) — ADR-005 (Redis), ADR-006 (Kafka)
- [`system-architecture.md`](file:///home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/architecture/system-architecture.md) — §8 (Redis), §9 (Kafka)
- [`orders.md`](file:///home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/api/orders.md) — §8.3, §8.4, §11
- [`sequence-diagrams.md`](file:///home/hieunv1/Documents/ai-tech-marketplace/ai-tech-marketplace-docs/diagrams/sequence-diagrams.md) — §3

## Global Constraints

- Java 21, Spring Boot 4.1.1 — no version downgrades.
- All existing 205 tests must remain green after every task.
- Endpoints use `/api/v1/` prefix, responses wrapped in `ApiResponse<T>`.
- Kafka serialization: JSON with Jackson (`JsonSerializer` / `JsonDeserializer`).
- Redis connection: localhost:6379 (already running).
- Kafka broker: localhost:9092 via Docker Compose (KRaft single-node, no Zookeeper).
- Domain events are published *after* the DB transaction commits (via `@TransactionalEventListener`).
- Consumers must be idempotent (per ADR-006).

## Review Focus

1. **Double-submit with expired lock:** If a Redis lock expires mid-transaction (e.g., long GC pause), a second checkout could proceed — ensure lock TTL (30s) far exceeds the longest expected transaction time and test the timeout path.
2. **Event lost on app crash after commit:** Publishing events via `@TransactionalEventListener(phase = AFTER_COMMIT)` means events are lost if the app crashes between commit and Kafka send — acceptable for v1 (notification/analytics), document the trade-off.
3. **Oversell with high concurrency on same product:** Pessimistic lock on product rows prevents this, but test that two concurrent checkouts for the same product correctly serialize stock deduction.
4. **Kafka consumer retry storm:** A poisoned event (bad JSON, unknown field) retries infinitely by default — configure `DefaultErrorHandler` with fixed backoff and dead-letter topic.
5. **Redis unavailable at checkout:** If Redis is down, `RedisLockRegistry.obtain().tryLock()` throws — the checkout must fail gracefully with 503 Service Unavailable, not 500.

---

### Task 1: Infrastructure & Dependencies

**Files:**
- Modify: `docker-compose.yml`
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/resources/application.yaml`

**Interfaces:**
- Consumes: nothing
- Produces: Kafka broker at `localhost:9092`, Redis at `localhost:6379`, Maven dependencies `spring-boot-starter-data-redis`, `spring-kafka`, `spring-integration-redis`, `spring-kafka-test`

- [ ] **Step 1: Add Kafka (KRaft) and Redis services to `docker-compose.yml`**

Add `kafka` service (image `apache/kafka:3.9.0`, single-node KRaft, port `9092`, env `KAFKA_NODE_ID=1`, `KAFKA_PROCESS_ROLES=broker,controller`, `KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093`, `KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093`, `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1`) and `redis` service (image `redis:7-alpine`, port `6379`, healthcheck `redis-cli ping`). Add `kafka_data` and `redis_data` volumes.

- [ ] **Step 2: Add Maven dependencies to `pom.xml`**

Add `spring-boot-starter-data-redis`, `spring-kafka`, `spring-integration-redis` as compile dependencies. Add `spring-kafka-test` as test dependency. No version tags — managed by Spring Boot BOM.

- [ ] **Step 3: Add Redis and Kafka config to `application.yaml`**

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: marketplace
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.aitechmarketplace.backend.event
```

- [ ] **Step 4: Add Redis config to test `application.yaml`**

Add same `spring.data.redis` section. Add `spring.kafka.bootstrap-servers: ${spring.embedded.kafka.brokers}` placeholder (overridden by `@EmbeddedKafka`).

- [ ] **Step 5: Start Docker services and verify connectivity**

Run: `docker compose up -d kafka redis`
Verify: `docker compose ps` shows both healthy. `docker exec ai-tech-marketplace-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list` returns without error. `redis-cli -p 6379 ping` returns `PONG`.

- [ ] **Step 6: Run existing test suite**

Run: `./mvnw clean test`
Expected: 205 tests pass, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add docker-compose.yml pom.xml src/main/resources/application.yaml src/test/resources/application.yaml
git commit -m "chore: add Kafka, Redis infrastructure and Spring dependencies"
```

---

### Task 2: Redis Configuration & Distributed Lock Bean

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/config/RedisConfig.java`
- Create: `src/test/java/com/aitechmarketplace/backend/config/RedisConfigTest.java`

**Interfaces:**
- Consumes: `spring-boot-starter-data-redis`, `spring-integration-redis` (from Task 1)
- Produces: `RedisLockRegistry` bean named `"checkoutLockRegistry"` with registry key `"checkout"` and default TTL 30000ms. `RedisConnectionFactory` auto-configured by Spring Boot.

- [ ] **Step 1: Write the failing test**

```java
@SpringBootTest
class RedisConfigTest {
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    @Qualifier("checkoutLockRegistry")
    private RedisLockRegistry checkoutLockRegistry;

    @Test
    void redisConnectionFactory_shouldBeConfigured() {
        assertThat(redisConnectionFactory).isNotNull();
    }

    @Test
    void checkoutLockRegistry_shouldBeConfigured() {
        assertThat(checkoutLockRegistry).isNotNull();
        Lock lock = checkoutLockRegistry.obtain("test-key");
        assertThat(lock).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=RedisConfigTest -Dspring.kafka.bootstrap-servers=localhost:9092`
Expected: FAIL — `No qualifying bean of type 'RedisLockRegistry'`

- [ ] **Step 3: Implement `RedisConfig`**

`@Configuration` class. Bean method `checkoutLockRegistry(RedisConnectionFactory)` returns `new RedisLockRegistry(connectionFactory, "checkout", 30_000L)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl . -Dtest=RedisConfigTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aitechmarketplace/backend/config/RedisConfig.java \
        src/test/java/com/aitechmarketplace/backend/config/RedisConfigTest.java
git commit -m "feat(redis): configure RedisLockRegistry for checkout distributed locking"
```

---

### Task 3: Domain Event Model

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/event/OrderCreatedEvent.java`
- Create: `src/main/java/com/aitechmarketplace/backend/event/OrderCancelledEvent.java`
- Create: `src/main/java/com/aitechmarketplace/backend/event/EventTopics.java`
- Create: `src/test/java/com/aitechmarketplace/backend/event/DomainEventSerializationTest.java`

**Interfaces:**
- Consumes: `Order` entity, `OrderItem` entity (existing)
- Produces:
  - `record OrderCreatedEvent(String eventId, String orderNumber, Long userId, BigDecimal totalAmount, String paymentMethod, List<OrderItemEvent> items, OffsetDateTime occurredAt)`
  - `record OrderCancelledEvent(String eventId, String orderNumber, Long userId, String reason, OffsetDateTime occurredAt)`
  - `record OrderItemEvent(Long productId, String productName, int quantity, BigDecimal unitPrice)`
  - `class EventTopics` with `public static final String ORDERS = "marketplace.orders";`

- [ ] **Step 1: Write the serialization test**

```java
class DomainEventSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void orderCreatedEvent_serializesAndDeserializes() {
        var event = new OrderCreatedEvent(
            UUID.randomUUID().toString(),
            "ORD-20261014-ABCD1234",
            1L,
            new BigDecimal("500000.00"),
            "COD",
            List.of(new OrderItemEvent(10L, "Product A", 2, new BigDecimal("250000.00"))),
            OffsetDateTime.now()
        );
        String json = mapper.writeValueAsString(event);
        OrderCreatedEvent deserialized = mapper.readValue(json, OrderCreatedEvent.class);
        assertThat(deserialized.orderNumber()).isEqualTo("ORD-20261014-ABCD1234");
        assertThat(deserialized.items()).hasSize(1);
    }

    @Test
    void orderCancelledEvent_serializesAndDeserializes() {
        var event = new OrderCancelledEvent(
            UUID.randomUUID().toString(),
            "ORD-20261014-ABCD1234",
            1L,
            "User requested cancellation",
            OffsetDateTime.now()
        );
        String json = mapper.writeValueAsString(event);
        OrderCancelledEvent deserialized = mapper.readValue(json, OrderCancelledEvent.class);
        assertThat(deserialized.reason()).isEqualTo("User requested cancellation");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=DomainEventSerializationTest`
Expected: FAIL — classes not found

- [ ] **Step 3: Implement event records and `EventTopics`**

Plain Java records with Jackson-compatible constructors (records are supported by default). `EventTopics` is a final class with a private constructor and `public static final String ORDERS = "marketplace.orders";`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl . -Dtest=DomainEventSerializationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aitechmarketplace/backend/event/ \
        src/test/java/com/aitechmarketplace/backend/event/
git commit -m "feat(event): define OrderCreatedEvent, OrderCancelledEvent domain event records"
```

---

### Task 4: Kafka Producer — `DomainEventPublisher`

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/event/DomainEventPublisher.java`
- Create: `src/test/java/com/aitechmarketplace/backend/event/DomainEventPublisherTest.java`

**Interfaces:**
- Consumes: `KafkaTemplate<String, Object>` (auto-configured), `EventTopics.ORDERS` (Task 3)
- Produces: `DomainEventPublisher` Spring `@Component` with methods:
  - `void publishOrderCreated(OrderCreatedEvent event)` — sends to `EventTopics.ORDERS` with key `event.orderNumber()`
  - `void publishOrderCancelled(OrderCancelledEvent event)` — sends to `EventTopics.ORDERS` with key `event.orderNumber()`

- [ ] **Step 1: Write the failing test**

```java
@EmbeddedKafka(partitions = 1, topics = EventTopics.ORDERS)
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class DomainEventPublisherTest {
    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    void publishOrderCreated_sendsToKafka() {
        var event = new OrderCreatedEvent(
            UUID.randomUUID().toString(), "ORD-TEST-001", 1L,
            new BigDecimal("100000"), "COD", List.of(), OffsetDateTime.now()
        );
        publisher.publishOrderCreated(event);

        var consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafka);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.aitechmarketplace.backend.event");
        var consumer = new DefaultKafkaConsumerFactory<String, OrderCreatedEvent>(consumerProps).createConsumer();
        embeddedKafka.consumeFromEmbeddedTopics(consumer, EventTopics.ORDERS);

        var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThanOrEqualTo(1);
        consumer.close();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=DomainEventPublisherTest`
Expected: FAIL — `DomainEventPublisher` not found

- [ ] **Step 3: Implement `DomainEventPublisher`**

`@Component` with constructor-injected `KafkaTemplate<String, Object>`. Each publish method calls `kafkaTemplate.send(EventTopics.ORDERS, event.orderNumber(), event)`. Log success/failure via `CompletableFuture` callback with SLF4J.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl . -Dtest=DomainEventPublisherTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aitechmarketplace/backend/event/DomainEventPublisher.java \
        src/test/java/com/aitechmarketplace/backend/event/DomainEventPublisherTest.java
git commit -m "feat(event): implement DomainEventPublisher with KafkaTemplate"
```

---

### Task 5: Pessimistic Locking on Product Stock

**Files:**
- Modify: `src/main/java/com/aitechmarketplace/backend/product/repository/ProductRepository.java`
- Modify: `src/main/java/com/aitechmarketplace/backend/order/service/OrderServiceImpl.java` (lines 96-106, 143-158, 212-218)
- Create: `src/test/java/com/aitechmarketplace/backend/order/service/OrderServiceStockLockingTest.java`

**Interfaces:**
- Consumes: `ProductRepository`, `OrderServiceImpl.createOrder()`, `OrderServiceImpl.cancelOrder()` (existing)
- Produces:
  - New method in `ProductRepository`: `@Lock(PESSIMISTIC_WRITE) @Query("SELECT p FROM Product p WHERE p.id = :id") Optional<Product> findByIdForUpdate(@Param("id") Long id)`
  - `OrderServiceImpl.createOrder()` uses `findByIdForUpdate()` instead of `item.getProduct()` for stock deduction
  - `OrderServiceImpl.cancelOrder()` uses `findByIdForUpdate()` for stock rollback

- [ ] **Step 1: Write the concurrency test**

```java
@SpringBootTest
class OrderServiceStockLockingTest {
    @Autowired private ProductRepository productRepository;

    @Test
    void findByIdForUpdate_acquiresPessimisticLock() {
        // Verify the method exists and returns a product
        // (Actual lock verification requires concurrent transaction test, covered in Task 8)
        Product product = productRepository.findByIdForUpdate(existingProductId);
        assertThat(product).isPresent();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=OrderServiceStockLockingTest`
Expected: FAIL — method not found

- [ ] **Step 3: Add `findByIdForUpdate` to `ProductRepository`**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdForUpdate(@Param("id") Long id);
```

- [ ] **Step 4: Update `OrderServiceImpl.createOrder()` stock deduction loop**

Replace `item.getProduct()` lookup with `productRepository.findByIdForUpdate(item.getProduct().getId())` in the stock validation loop (lines 96-106) and the stock deduction loop (lines 143-158). Merge both loops into a single loop: validate stock → deduct stock → create `OrderItem`, all while holding the pessimistic lock.

- [ ] **Step 5: Update `OrderServiceImpl.cancelOrder()` stock rollback**

Replace `item.getProduct()` with `productRepository.findByIdForUpdate(item.getProduct().getId())` in the stock rollback loop (lines 212-218).

- [ ] **Step 6: Run full test suite**

Run: `./mvnw clean test`
Expected: All 205+ tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/aitechmarketplace/backend/product/repository/ProductRepository.java \
        src/main/java/com/aitechmarketplace/backend/order/service/OrderServiceImpl.java \
        src/test/java/com/aitechmarketplace/backend/order/service/OrderServiceStockLockingTest.java
git commit -m "feat(order): add pessimistic locking on product stock during checkout"
```

---

### Task 6: Redis Distributed Lock on Checkout

**Files:**
- Modify: `src/main/java/com/aitechmarketplace/backend/order/service/OrderServiceImpl.java` (constructor, `createOrder()`)
- Create: `src/test/java/com/aitechmarketplace/backend/order/service/OrderServiceDistributedLockTest.java`

**Interfaces:**
- Consumes: `RedisLockRegistry` bean `"checkoutLockRegistry"` (Task 2), `OrderServiceImpl.createOrder()` (existing)
- Produces: `createOrder()` acquires `checkoutLockRegistry.obtain("user:" + userId).tryLock(5, SECONDS)` before executing checkout logic; throws `ConflictException("Checkout already in progress", "CHECKOUT_IN_PROGRESS")` if lock not acquired; lock released in `finally` block.

- [ ] **Step 1: Write the failing test**

```java
@SpringBootTest
class OrderServiceDistributedLockTest {
    @Autowired private OrderService orderService;
    @MockitoBean private RedisLockRegistry checkoutLockRegistry;

    @Test
    void createOrder_throwsConflict_whenLockNotAcquired() throws Exception {
        Lock mockLock = mock(Lock.class);
        when(checkoutLockRegistry.obtain("user:1")).thenReturn(mockLock);
        when(mockLock.tryLock(5, TimeUnit.SECONDS)).thenReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(1L, validRequest))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Checkout already in progress");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=OrderServiceDistributedLockTest`
Expected: FAIL — no lock logic in `createOrder()`

- [ ] **Step 3: Inject `RedisLockRegistry` into `OrderServiceImpl` and wrap `createOrder()` with lock**

Add `@Qualifier("checkoutLockRegistry") RedisLockRegistry checkoutLockRegistry` to constructor. In `createOrder()`:
1. `Lock lock = checkoutLockRegistry.obtain("user:" + userId);`
2. `if (!lock.tryLock(5, TimeUnit.SECONDS))` → throw `ConflictException`
3. `try { ... existing logic ... } finally { lock.unlock(); }`
4. Catch `InterruptedException` → re-interrupt thread, throw `ConflictException`

**Review Focus test — Redis unavailable:**
Add test `createOrder_returns503_whenRedisUnavailable` that mocks `checkoutLockRegistry.obtain()` to throw `RedisConnectionException`, assert service throws appropriate exception (catch in `GlobalExceptionHandler` as 503).

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl . -Dtest=OrderServiceDistributedLockTest`
Expected: PASS

- [ ] **Step 5: Run full test suite**

Run: `./mvnw clean test`
Expected: All tests pass (existing tests need `RedisLockRegistry` mock — verify auto-wiring in test context).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aitechmarketplace/backend/order/service/OrderServiceImpl.java \
        src/test/java/com/aitechmarketplace/backend/order/service/OrderServiceDistributedLockTest.java
git commit -m "feat(order): add Redis distributed lock to prevent concurrent checkout"
```

---

### Task 7: Integrate Event Publishing into Order Service

**Files:**
- Modify: `src/main/java/com/aitechmarketplace/backend/order/service/OrderServiceImpl.java` (constructor, `createOrder()`, `cancelOrder()`)
- Create: `src/main/java/com/aitechmarketplace/backend/event/OrderEventBridge.java`
- Create: `src/test/java/com/aitechmarketplace/backend/event/OrderEventBridgeTest.java`

**Interfaces:**
- Consumes: `DomainEventPublisher` (Task 4), `OrderCreatedEvent`, `OrderCancelledEvent`, `OrderItemEvent` (Task 3), `ApplicationEventPublisher` (Spring)
- Produces:
  - `OrderEventBridge`: `@Component` with `@TransactionalEventListener(phase = AFTER_COMMIT)` handlers. Listens for Spring `ApplicationEvent` wrappers `OrderCreatedApplicationEvent(Order)` and `OrderCancelledApplicationEvent(Order, String reason)`, maps to domain event records, and delegates to `DomainEventPublisher`.
  - `OrderServiceImpl` publishes `OrderCreatedApplicationEvent` via `applicationEventPublisher.publishEvent()` after `orderRepository.save()`, and `OrderCancelledApplicationEvent` after setting status to `CANCELLED`.

- [ ] **Step 1: Write the failing test**

```java
@EmbeddedKafka(partitions = 1, topics = EventTopics.ORDERS)
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class OrderEventBridgeTest {
    @Autowired private ApplicationEventPublisher appEventPublisher;
    @MockitoBean private DomainEventPublisher domainEventPublisher;

    @Test
    @Transactional
    void afterOrderCreated_publishesToKafka() {
        // Simulate Spring event after commit
        Order order = createTestOrder();
        appEventPublisher.publishEvent(new OrderCreatedApplicationEvent(order));
        verify(domainEventPublisher).publishOrderCreated(argThat(e ->
            e.orderNumber().equals(order.getOrderNumber())
        ));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=OrderEventBridgeTest`
Expected: FAIL — classes not found

- [ ] **Step 3: Implement `OrderCreatedApplicationEvent`, `OrderCancelledApplicationEvent` (inner records/classes of `OrderEventBridge`), and `OrderEventBridge`**

`OrderEventBridge` has two `@TransactionalEventListener(phase = AFTER_COMMIT)` methods, one for each application event type. Each maps the `Order` entity to the corresponding domain event record and calls `domainEventPublisher.publishOrderCreated/publishOrderCancelled`.

- [ ] **Step 4: Inject `ApplicationEventPublisher` into `OrderServiceImpl`**

Add `ApplicationEventPublisher` to constructor. After `orderRepository.save()` in `createOrder()`, call `applicationEventPublisher.publishEvent(new OrderCreatedApplicationEvent(savedOrder))`. After `order.setStatus(CANCELLED)` and save in `cancelOrder()`, call `applicationEventPublisher.publishEvent(new OrderCancelledApplicationEvent(savedOrder, "User requested cancellation"))`.

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -pl . -Dtest=OrderEventBridgeTest`
Expected: PASS

- [ ] **Step 6: Run full test suite**

Run: `./mvnw clean test`
Expected: All tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/aitechmarketplace/backend/event/OrderEventBridge.java \
        src/main/java/com/aitechmarketplace/backend/order/service/OrderServiceImpl.java \
        src/test/java/com/aitechmarketplace/backend/event/OrderEventBridgeTest.java
git commit -m "feat(event): publish OrderCreated/OrderCancelled events after transaction commit"
```

---

### Task 8: Kafka Consumers — Notification & Analytics

**Files:**
- Create: `src/main/java/com/aitechmarketplace/backend/event/consumer/OrderNotificationConsumer.java`
- Create: `src/main/java/com/aitechmarketplace/backend/event/consumer/OrderAnalyticsConsumer.java`
- Create: `src/main/java/com/aitechmarketplace/backend/config/KafkaConfig.java`
- Create: `src/test/java/com/aitechmarketplace/backend/event/consumer/OrderNotificationConsumerTest.java`

**Interfaces:**
- Consumes: `EventTopics.ORDERS` (Task 3), `OrderCreatedEvent`, `OrderCancelledEvent` (Task 3)
- Produces:
  - `OrderNotificationConsumer`: `@KafkaListener(topics = EventTopics.ORDERS, groupId = "notification")` — logs order events at INFO level (placeholder for email integration).
  - `OrderAnalyticsConsumer`: `@KafkaListener(topics = EventTopics.ORDERS, groupId = "analytics")` — logs order events at INFO level (placeholder for analytics pipeline).
  - `KafkaConfig`: configures `DefaultErrorHandler` with `FixedBackOff(1000, 3)` and `DeadLetterPublishingRecoverer` to `marketplace.orders.DLT`.

- [ ] **Step 1: Write the failing test**

```java
@EmbeddedKafka(partitions = 1, topics = {EventTopics.ORDERS})
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class OrderNotificationConsumerTest {
    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @SpyBean private OrderNotificationConsumer consumer;

    @Test
    void consumesOrderCreatedEvent() throws Exception {
        var event = new OrderCreatedEvent(
            UUID.randomUUID().toString(), "ORD-TEST-CONSUMER", 1L,
            new BigDecimal("100000"), "COD", List.of(), OffsetDateTime.now()
        );
        kafkaTemplate.send(EventTopics.ORDERS, event.orderNumber(), event).get();

        // Allow consumer time to process
        verify(consumer, timeout(10000)).handleOrderEvent(argThat(e ->
            e instanceof OrderCreatedEvent created &&
            created.orderNumber().equals("ORD-TEST-CONSUMER")
        ));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=OrderNotificationConsumerTest`
Expected: FAIL — class not found

- [ ] **Step 3: Implement `KafkaConfig`**

`@Configuration` class with `@Bean DefaultErrorHandler kafkaErrorHandler(KafkaTemplate)` returning `new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), new FixedBackOff(1000L, 3L))`.

- [ ] **Step 4: Implement `OrderNotificationConsumer` and `OrderAnalyticsConsumer`**

Both are `@Component` classes with `@KafkaListener`. The listener method signature: `void handleOrderEvent(Object event)` with `@Payload`. Use `instanceof` pattern matching to handle `OrderCreatedEvent` and `OrderCancelledEvent`. Log at INFO level.

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -pl . -Dtest=OrderNotificationConsumerTest`
Expected: PASS

- [ ] **Step 6: Run full test suite**

Run: `./mvnw clean test`
Expected: All tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/aitechmarketplace/backend/event/consumer/ \
        src/main/java/com/aitechmarketplace/backend/config/KafkaConfig.java \
        src/test/java/com/aitechmarketplace/backend/event/consumer/
git commit -m "feat(event): add Kafka consumers for order notification and analytics"
```

---

### Task 9: End-to-End Integration Test & Final Verification

**Files:**
- Create: `src/test/java/com/aitechmarketplace/backend/order/OrderCheckoutIntegrationTest.java`

**Interfaces:**
- Consumes: All of the above — Redis lock, pessimistic locking, event publishing, Kafka consumers
- Produces: Full end-to-end test that proves: checkout acquires lock → deducts stock with pessimistic lock → publishes event → consumer receives event

- [ ] **Step 1: Write the integration test**

```java
@EmbeddedKafka(partitions = 1, topics = EventTopics.ORDERS)
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class OrderCheckoutIntegrationTest {
    @Autowired private OrderService orderService;
    @SpyBean private DomainEventPublisher domainEventPublisher;
    // Setup: user, address, cart with items, product with stock

    @Test
    @Transactional
    void checkout_deductsStock_andPublishesEvent() {
        OrderResponse response = orderService.createOrder(userId, request);

        assertThat(response.orderNumber()).startsWith("ORD-");
        // Verify stock was deducted
        Product updated = productRepository.findById(productId).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualTo(initialStock - quantity);
    }

    @Test
    void concurrentCheckout_sameProduct_serializes() throws Exception {
        // Two threads checkout the same product simultaneously
        // Both should succeed (or one fails with out-of-stock) — no oversell
        ExecutorService executor = Executors.newFixedThreadPool(2);
        // ... concurrent test setup ...
        assertThat(product.getStockQuantity()).isGreaterThanOrEqualTo(0);
    }
}
```

- [ ] **Step 2: Run the integration test**

Run: `./mvnw test -pl . -Dtest=OrderCheckoutIntegrationTest`
Expected: PASS

- [ ] **Step 3: Run full test suite**

Run: `./mvnw clean test`
Expected: All tests pass (205 original + new tests).

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/aitechmarketplace/backend/order/OrderCheckoutIntegrationTest.java
git commit -m "test(order): add end-to-end checkout integration test with Kafka and Redis"
```
