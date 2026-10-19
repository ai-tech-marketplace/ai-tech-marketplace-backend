package com.aitechmarketplace.backend.settlement.repository;

import com.aitechmarketplace.backend.analytics.repository.CategoryGmvProjection;
import com.aitechmarketplace.backend.analytics.repository.DailyRevenueProjection;
import com.aitechmarketplace.backend.analytics.repository.TopSellerProjection;
import com.aitechmarketplace.backend.settlement.entity.SellerOrderSettlement;
import com.aitechmarketplace.backend.settlement.entity.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface SellerOrderSettlementRepository extends JpaRepository<SellerOrderSettlement, Long> {

    List<SellerOrderSettlement> findByOrderId(Long orderId);

    List<SellerOrderSettlement> findBySellerId(Long sellerId);

    Page<SellerOrderSettlement> findBySellerIdAndStatus(Long sellerId, SettlementStatus status, Pageable pageable);

    Page<SellerOrderSettlement> findBySellerId(Long sellerId, Pageable pageable);

    @Query(value = """
        SELECT COALESCE(SUM(commission_amount), 0)
        FROM seller_order_settlements
        WHERE created_at >= :startDate AND created_at <= :endDate
          AND status != 'CANCELLED'
    """, nativeQuery = true)
    BigDecimal getNetPlatformRevenue(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate
    );

    @Query(value = """
        SELECT s.seller_id AS sellerId,
               u.full_name AS sellerName,
               COUNT(DISTINCT s.order_id) AS totalOrders,
               COALESCE(SUM(s.item_subtotal), 0) AS grossSales,
               COALESCE(SUM(s.commission_amount), 0) AS commissionPaid,
               COALESCE(SUM(s.seller_net_amount), 0) AS sellerNetAmount
        FROM seller_order_settlements s
        JOIN users u ON s.seller_id = u.id
        WHERE s.created_at >= :startDate AND s.created_at <= :endDate
          AND s.status != 'CANCELLED'
        GROUP BY s.seller_id, u.full_name
        ORDER BY grossSales DESC
        LIMIT :limit
    """, nativeQuery = true)
    List<TopSellerProjection> getTopSellers(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            @Param("limit") int limit
    );

    @Query(value = """
        SELECT c.id AS categoryId,
               c.name AS categoryName,
               COALESCE(SUM(oi.quantity), 0) AS totalUnitsSold,
               COALESCE(SUM(s.item_subtotal), 0) AS gmv,
               COALESCE(SUM(s.commission_amount), 0) AS platformCommissionEarned
        FROM seller_order_settlements s
        JOIN order_items oi ON s.order_item_id = oi.id
        JOIN products p ON oi.product_id = p.id
        JOIN categories c ON p.category_id = c.id
        WHERE s.created_at >= :startDate AND s.created_at <= :endDate
          AND s.status != 'CANCELLED'
        GROUP BY c.id, c.name
        ORDER BY gmv DESC
    """, nativeQuery = true)
    List<CategoryGmvProjection> getCategoryBreakdown(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate
    );

    @Query(value = """
        SELECT TO_CHAR(s.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS dateStr,
               COALESCE(SUM(s.item_subtotal), 0) AS gmv,
               COALESCE(SUM(s.commission_amount), 0) AS commission,
               COUNT(DISTINCT s.order_id) AS orderCount
        FROM seller_order_settlements s
        WHERE s.created_at >= :startDate AND s.created_at <= :endDate
          AND s.status != 'CANCELLED'
        GROUP BY TO_CHAR(s.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD')
        ORDER BY dateStr ASC
    """, nativeQuery = true)
    List<DailyRevenueProjection> getDailyRevenueTimeline(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate
    );
}
