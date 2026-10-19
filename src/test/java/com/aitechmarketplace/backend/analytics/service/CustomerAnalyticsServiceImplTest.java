package com.aitechmarketplace.backend.analytics.service;

import com.aitechmarketplace.backend.analytics.dto.customer.CategorySpendingResponse;
import com.aitechmarketplace.backend.analytics.dto.customer.CustomerSpendingSummaryResponse;
import com.aitechmarketplace.backend.analytics.dto.customer.MonthlySpendingResponse;
import com.aitechmarketplace.backend.analytics.repository.CustomerCategorySpendingProjection;
import com.aitechmarketplace.backend.analytics.repository.CustomerMonthlySpendingProjection;
import com.aitechmarketplace.backend.analytics.repository.CustomerSpendingProjection;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAnalyticsServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private CustomerAnalyticsServiceImpl customerAnalyticsService;

    @Test
    void getSpendingSummary_shouldReturnCustomerSummary() {
        CustomerSpendingProjection summaryProj = mock(CustomerSpendingProjection.class);
        when(summaryProj.getTotalSpent()).thenReturn(new BigDecimal("15000000.00"));
        when(summaryProj.getTotalSaved()).thenReturn(new BigDecimal("1000000.00"));
        when(summaryProj.getTotalOrdersPlaced()).thenReturn(5L);
        when(summaryProj.getCompletedOrders()).thenReturn(4L);
        when(summaryProj.getFirstOrderDate()).thenReturn(Instant.parse("2026-08-01T10:00:00Z"));
        when(summaryProj.getLastOrderDate()).thenReturn(Instant.parse("2026-10-10T15:30:00Z"));

        when(orderRepository.getCustomerSpendingSummary(1L)).thenReturn(summaryProj);

        CustomerSpendingSummaryResponse response = customerAnalyticsService.getSpendingSummary(1L);

        assertThat(response.currency()).isEqualTo("VND");
        assertThat(response.totalSpent()).isEqualByComparingTo("15000000.00");
        assertThat(response.totalSaved()).isEqualByComparingTo("1000000.00");
        assertThat(response.totalOrdersPlaced()).isEqualTo(5L);
        assertThat(response.completedOrders()).isEqualTo(4L);
        assertThat(response.firstOrderDate()).isEqualTo(OffsetDateTime.parse("2026-08-01T10:00:00Z"));
    }

    @Test
    void getCategoryBreakdown_shouldComputePercentageShare() {
        CustomerCategorySpendingProjection cat1 = mock(CustomerCategorySpendingProjection.class);
        when(cat1.getCategoryId()).thenReturn(1L);
        when(cat1.getCategoryName()).thenReturn("Laptops");
        when(cat1.getAmountSpent()).thenReturn(new BigDecimal("30000000.00"));
        when(cat1.getItemsPurchased()).thenReturn(1L);

        CustomerCategorySpendingProjection cat2 = mock(CustomerCategorySpendingProjection.class);
        when(cat2.getCategoryId()).thenReturn(2L);
        when(cat2.getCategoryName()).thenReturn("Mice");
        when(cat2.getAmountSpent()).thenReturn(new BigDecimal("10000000.00"));
        when(cat2.getItemsPurchased()).thenReturn(2L);

        when(orderRepository.getCustomerCategoryBreakdown(1L)).thenReturn(List.of(cat1, cat2));

        List<CategorySpendingResponse> breakdown = customerAnalyticsService.getCategoryBreakdown(1L);

        assertThat(breakdown).hasSize(2);
        assertThat(breakdown.get(0).percentageShare()).isEqualByComparingTo("75.00");
        assertThat(breakdown.get(1).percentageShare()).isEqualByComparingTo("25.00");
    }

    @Test
    void getMonthlySpending_shouldReturnMonthlyPoints() {
        CustomerMonthlySpendingProjection p1 = mock(CustomerMonthlySpendingProjection.class);
        when(p1.getMonthStr()).thenReturn("2026-09");
        when(p1.getAmountSpent()).thenReturn(new BigDecimal("10000000.00"));
        when(p1.getOrderCount()).thenReturn(2L);

        when(orderRepository.getCustomerMonthlySpending(eq(1L), any(OffsetDateTime.class)))
                .thenReturn(List.of(p1));

        MonthlySpendingResponse response = customerAnalyticsService.getMonthlySpending(1L, 6);

        assertThat(response.months()).isEqualTo(6);
        assertThat(response.timeline()).hasSize(1);
        assertThat(response.timeline().get(0).month()).isEqualTo("2026-09");
    }
}
