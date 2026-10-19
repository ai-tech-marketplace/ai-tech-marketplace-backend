package com.aitechmarketplace.backend.order.repository;

import com.aitechmarketplace.backend.analytics.repository.AdminOverviewOrdersProjection;
import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
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
}
