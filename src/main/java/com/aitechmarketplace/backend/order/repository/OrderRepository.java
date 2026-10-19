package com.aitechmarketplace.backend.order.repository;

import com.aitechmarketplace.backend.analytics.repository.AdminOverviewOrdersProjection;
import com.aitechmarketplace.backend.analytics.repository.CustomerCategorySpendingProjection;
import com.aitechmarketplace.backend.analytics.repository.CustomerMonthlySpendingProjection;
import com.aitechmarketplace.backend.analytics.repository.CustomerSpendingProjection;
import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    Optional<Order> findByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"items"})
    Page<Order> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    @Query(value = """
        SELECT COUNT(*) AS totalOrders,
               COUNT(CASE WHEN status = 'DELIVERED' THEN 1 END) AS completedOrders,
               COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelledOrders,
               COALESCE(SUM(CASE WHEN status != 'CANCELLED' THEN total_amount ELSE 0 END), 0) AS gmv,
               COALESCE(SUM(CASE WHEN status != 'CANCELLED' THEN discount_amount ELSE 0 END), 0) AS totalDiscounts
        FROM orders
        WHERE created_at >= :startDate AND created_at <= :endDate
    """, nativeQuery = true)
    AdminOverviewOrdersProjection getAdminOverviewOrders(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate
    );

    @Query(value = """
        SELECT COALESCE(SUM(CASE WHEN status != 'CANCELLED' THEN total_amount ELSE 0 END), 0) AS totalSpent,
               COALESCE(SUM(CASE WHEN status != 'CANCELLED' THEN discount_amount ELSE 0 END), 0) AS totalSaved,
               COUNT(*) AS totalOrdersPlaced,
               COUNT(CASE WHEN status = 'DELIVERED' THEN 1 END) AS completedOrders,
               MIN(created_at) AS firstOrderDate,
               MAX(created_at) AS lastOrderDate
        FROM orders
        WHERE user_id = :userId
    """, nativeQuery = true)
    CustomerSpendingProjection getCustomerSpendingSummary(@Param("userId") Long userId);

    @Query(value = """
        SELECT c.id AS categoryId,
               c.name AS categoryName,
               COALESCE(SUM(oi.subtotal), 0) AS amountSpent,
               COALESCE(SUM(oi.quantity), 0) AS itemsPurchased
        FROM orders o
        JOIN order_items oi ON o.id = oi.order_id
        JOIN products p ON oi.product_id = p.id
        JOIN categories c ON p.category_id = c.id
        WHERE o.user_id = :userId
          AND o.status != 'CANCELLED'
        GROUP BY c.id, c.name
        ORDER BY amountSpent DESC
    """, nativeQuery = true)
    List<CustomerCategorySpendingProjection> getCustomerCategoryBreakdown(@Param("userId") Long userId);

    @Query(value = """
        SELECT TO_CHAR(created_at AT TIME ZONE 'UTC', 'YYYY-MM') AS monthStr,
               COALESCE(SUM(CASE WHEN status != 'CANCELLED' THEN total_amount ELSE 0 END), 0) AS amountSpent,
               COUNT(CASE WHEN status != 'CANCELLED' THEN 1 END) AS orderCount
        FROM orders
        WHERE user_id = :userId
          AND created_at >= :startDate
        GROUP BY TO_CHAR(created_at AT TIME ZONE 'UTC', 'YYYY-MM')
        ORDER BY monthStr ASC
    """, nativeQuery = true)
    List<CustomerMonthlySpendingProjection> getCustomerMonthlySpending(
            @Param("userId") Long userId,
            @Param("startDate") OffsetDateTime startDate
    );

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    @Query("""
        SELECT o FROM Order o
        WHERE o.status = com.aitechmarketplace.backend.order.entity.OrderStatus.PENDING
          AND o.paymentStatus = com.aitechmarketplace.backend.order.entity.PaymentStatus.PENDING
          AND (o.paymentMethod IS NULL OR o.paymentMethod != com.aitechmarketplace.backend.order.entity.PaymentMethod.COD)
          AND o.createdAt < :cutoffTime
    """)
    List<Order> findExpiredPendingOrders(@Param("cutoffTime") OffsetDateTime cutoffTime);
}
