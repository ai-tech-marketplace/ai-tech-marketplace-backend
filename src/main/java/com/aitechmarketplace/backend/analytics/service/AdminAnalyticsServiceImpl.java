package com.aitechmarketplace.backend.analytics.service;

import com.aitechmarketplace.backend.analytics.dto.admin.*;
import com.aitechmarketplace.backend.analytics.repository.AdminOverviewOrdersProjection;
import com.aitechmarketplace.backend.analytics.repository.CategoryGmvProjection;
import com.aitechmarketplace.backend.analytics.repository.DailyRevenueProjection;
import com.aitechmarketplace.backend.analytics.repository.TopSellerProjection;
import com.aitechmarketplace.backend.analytics.util.DateRangeParser;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.settlement.repository.SellerOrderSettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {

    private final OrderRepository orderRepository;
    private final SellerOrderSettlementRepository settlementRepository;

    public AdminAnalyticsServiceImpl(
            OrderRepository orderRepository,
            SellerOrderSettlementRepository settlementRepository
    ) {
        this.orderRepository = orderRepository;
        this.settlementRepository = settlementRepository;
    }

    @Override
    public PlatformAnalyticsOverviewResponse getOverview(String period, String startDate, String endDate) {
        DateRangeParser.DateRange range = DateRangeParser.parse(period, startDate, endDate);

        AdminOverviewOrdersProjection ordersProj = orderRepository.getAdminOverviewOrders(range.start(), range.end());
        BigDecimal netRev = settlementRepository.getNetPlatformRevenue(range.start(), range.end());
        if (netRev == null) {
            netRev = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        long totalOrders = ordersProj != null && ordersProj.getTotalOrders() != null ? ordersProj.getTotalOrders() : 0L;
        long completedOrders = ordersProj != null && ordersProj.getCompletedOrders() != null ? ordersProj.getCompletedOrders() : 0L;
        long cancelledOrders = ordersProj != null && ordersProj.getCancelledOrders() != null ? ordersProj.getCancelledOrders() : 0L;
        BigDecimal gmv = ordersProj != null && ordersProj.getGmv() != null ? ordersProj.getGmv() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDiscounts = ordersProj != null && ordersProj.getTotalDiscounts() != null ? ordersProj.getTotalDiscounts() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal cancellationRate = totalOrders > 0
                ? BigDecimal.valueOf(cancelledOrders).multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        long nonCancelledOrders = totalOrders - cancelledOrders;
        BigDecimal averageOrderValue = nonCancelledOrders > 0
                ? gmv.divide(BigDecimal.valueOf(nonCancelledOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return new PlatformAnalyticsOverviewResponse(
                "VND",
                range.start(),
                range.end(),
                gmv,
                netRev,
                totalOrders,
                completedOrders,
                cancelledOrders,
                cancellationRate,
                totalDiscounts,
                averageOrderValue
        );
    }

    @Override
    public List<TopSellerResponse> getTopSellers(String period, String startDate, String endDate, Integer limit) {
        DateRangeParser.DateRange range = DateRangeParser.parse(period, startDate, endDate);
        int effectiveLimit = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);

        List<TopSellerProjection> projections = settlementRepository.getTopSellers(range.start(), range.end(), effectiveLimit);
        return projections.stream()
                .map(p -> new TopSellerResponse(
                        p.getSellerId(),
                        p.getSellerName(),
                        p.getTotalOrders() != null ? p.getTotalOrders() : 0L,
                        p.getGrossSales() != null ? p.getGrossSales() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        p.getCommissionPaid() != null ? p.getCommissionPaid() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        p.getSellerNetAmount() != null ? p.getSellerNetAmount() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                ))
                .toList();
    }

    @Override
    public List<CategoryGmvResponse> getCategoryBreakdown(String period, String startDate, String endDate) {
        DateRangeParser.DateRange range = DateRangeParser.parse(period, startDate, endDate);
        List<CategoryGmvProjection> projections = settlementRepository.getCategoryBreakdown(range.start(), range.end());

        BigDecimal totalGmv = projections.stream()
                .map(CategoryGmvProjection::getGmv)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return projections.stream()
                .map(p -> {
                    BigDecimal percentageShare = totalGmv.compareTo(BigDecimal.ZERO) > 0
                            ? p.getGmv().multiply(new BigDecimal("100")).divide(totalGmv, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

                    return new CategoryGmvResponse(
                            p.getCategoryId(),
                            p.getCategoryName(),
                            p.getTotalUnitsSold() != null ? p.getTotalUnitsSold() : 0L,
                            p.getGmv(),
                            percentageShare,
                            p.getPlatformCommissionEarned()
                    );
                })
                .toList();
    }

    @Override
    public PlatformRevenueChartResponse getRevenueChart(String period, String startDate, String endDate, String interval) {
        DateRangeParser.DateRange range = DateRangeParser.parse(period, startDate, endDate);
        String effectiveInterval = (interval == null || interval.isBlank()) ? "daily" : interval;

        List<DailyRevenueProjection> projections = settlementRepository.getDailyRevenueTimeline(range.start(), range.end());
        List<RevenueTimelinePoint> points = projections.stream()
                .map(p -> new RevenueTimelinePoint(
                        p.getDateStr(),
                        p.getGmv(),
                        p.getCommission(),
                        p.getOrderCount() != null ? p.getOrderCount() : 0L
                ))
                .toList();

        return new PlatformRevenueChartResponse(effectiveInterval, points);
    }
}
