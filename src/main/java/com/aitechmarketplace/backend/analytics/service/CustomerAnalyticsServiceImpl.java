package com.aitechmarketplace.backend.analytics.service;

import com.aitechmarketplace.backend.analytics.dto.customer.CategorySpendingResponse;
import com.aitechmarketplace.backend.analytics.dto.customer.CustomerSpendingSummaryResponse;
import com.aitechmarketplace.backend.analytics.dto.customer.MonthlySpendingPoint;
import com.aitechmarketplace.backend.analytics.dto.customer.MonthlySpendingResponse;
import com.aitechmarketplace.backend.analytics.repository.CustomerCategorySpendingProjection;
import com.aitechmarketplace.backend.analytics.repository.CustomerMonthlySpendingProjection;
import com.aitechmarketplace.backend.analytics.repository.CustomerSpendingProjection;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CustomerAnalyticsServiceImpl implements CustomerAnalyticsService {

    private final OrderRepository orderRepository;

    public CustomerAnalyticsServiceImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public CustomerSpendingSummaryResponse getSpendingSummary(Long userId) {
        CustomerSpendingProjection proj = orderRepository.getCustomerSpendingSummary(userId);

        BigDecimal totalSpent = (proj != null && proj.getTotalSpent() != null)
                ? proj.getTotalSpent()
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalSaved = (proj != null && proj.getTotalSaved() != null)
                ? proj.getTotalSaved()
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        long totalOrders = (proj != null && proj.getTotalOrdersPlaced() != null)
                ? proj.getTotalOrdersPlaced()
                : 0L;

        long completedOrders = (proj != null && proj.getCompletedOrders() != null)
                ? proj.getCompletedOrders()
                : 0L;

        OffsetDateTime firstOrderDate = (proj != null && proj.getFirstOrderDate() != null)
                ? proj.getFirstOrderDate().atOffset(ZoneOffset.UTC)
                : null;

        OffsetDateTime lastOrderDate = (proj != null && proj.getLastOrderDate() != null)
                ? proj.getLastOrderDate().atOffset(ZoneOffset.UTC)
                : null;

        return new CustomerSpendingSummaryResponse(
                "VND",
                totalSpent,
                totalSaved,
                totalOrders,
                completedOrders,
                firstOrderDate,
                lastOrderDate
        );
    }

    @Override
    public List<CategorySpendingResponse> getCategoryBreakdown(Long userId) {
        List<CustomerCategorySpendingProjection> projections = orderRepository.getCustomerCategoryBreakdown(userId);

        BigDecimal totalSpent = projections.stream()
                .map(CustomerCategorySpendingProjection::getAmountSpent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return projections.stream()
                .map(p -> {
                    BigDecimal percentageShare = totalSpent.compareTo(BigDecimal.ZERO) > 0
                            ? p.getAmountSpent().multiply(new BigDecimal("100")).divide(totalSpent, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

                    return new CategorySpendingResponse(
                            p.getCategoryId(),
                            p.getCategoryName(),
                            p.getAmountSpent(),
                            percentageShare,
                            p.getItemsPurchased() != null ? p.getItemsPurchased() : 0L
                    );
                })
                .toList();
    }

    @Override
    public MonthlySpendingResponse getMonthlySpending(Long userId, Integer months) {
        int effectiveMonths = (months == null || months <= 0) ? 6 : Math.min(months, 24);
        OffsetDateTime startDate = OffsetDateTime.now(ZoneOffset.UTC)
                .minusMonths(effectiveMonths)
                .with(LocalTime.MIN);

        List<CustomerMonthlySpendingProjection> projections = orderRepository.getCustomerMonthlySpending(userId, startDate);

        List<MonthlySpendingPoint> timeline = projections.stream()
                .map(p -> new MonthlySpendingPoint(
                        p.getMonthStr(),
                        p.getAmountSpent() != null ? p.getAmountSpent() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        p.getOrderCount() != null ? p.getOrderCount() : 0L
                ))
                .toList();

        return new MonthlySpendingResponse(effectiveMonths, timeline);
    }
}
