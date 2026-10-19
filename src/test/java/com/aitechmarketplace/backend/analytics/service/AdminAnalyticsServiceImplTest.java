package com.aitechmarketplace.backend.analytics.service;

import com.aitechmarketplace.backend.analytics.dto.admin.CategoryGmvResponse;
import com.aitechmarketplace.backend.analytics.dto.admin.PlatformAnalyticsOverviewResponse;
import com.aitechmarketplace.backend.analytics.dto.admin.PlatformRevenueChartResponse;
import com.aitechmarketplace.backend.analytics.dto.admin.TopSellerResponse;
import com.aitechmarketplace.backend.analytics.repository.AdminOverviewOrdersProjection;
import com.aitechmarketplace.backend.analytics.repository.CategoryGmvProjection;
import com.aitechmarketplace.backend.analytics.repository.DailyRevenueProjection;
import com.aitechmarketplace.backend.analytics.repository.TopSellerProjection;
import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.settlement.repository.SellerOrderSettlementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SellerOrderSettlementRepository settlementRepository;

    @InjectMocks
    private AdminAnalyticsServiceImpl adminAnalyticsService;

    @Test
    void getOverview_shouldReturnAggregatedMetrics() {
        AdminOverviewOrdersProjection ordersProj = mock(AdminOverviewOrdersProjection.class);
        when(ordersProj.getTotalOrders()).thenReturn(100L);
        when(ordersProj.getCompletedOrders()).thenReturn(80L);
        when(ordersProj.getCancelledOrders()).thenReturn(10L);
        when(ordersProj.getGmv()).thenReturn(new BigDecimal("100000000.00"));
        when(ordersProj.getTotalDiscounts()).thenReturn(new BigDecimal("5000000.00"));

        when(orderRepository.getAdminOverviewOrders(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(ordersProj);
        when(settlementRepository.getNetPlatformRevenue(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(new BigDecimal("5000000.00"));

        PlatformAnalyticsOverviewResponse response = adminAnalyticsService.getOverview("30d", null, null);

        assertThat(response.currency()).isEqualTo("VND");
        assertThat(response.totalOrders()).isEqualTo(100L);
        assertThat(response.completedOrders()).isEqualTo(80L);
        assertThat(response.cancelledOrders()).isEqualTo(10L);
        assertThat(response.cancellationRate()).isEqualByComparingTo("10.00");
        assertThat(response.gmv()).isEqualByComparingTo("100000000.00");
        assertThat(response.netPlatformRevenue()).isEqualByComparingTo("5000000.00");
        // 100,000,000 / 90 non-cancelled orders = 1111111.11
        assertThat(response.averageOrderValue()).isEqualByComparingTo("1111111.11");
    }

    @Test
    void getOverview_invalidDateRange_shouldThrow() {
        assertThatThrownBy(() -> adminAnalyticsService.getOverview(null, "2026-10-15", "2026-10-01"))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_DATE_RANGE");
    }

    @Test
    void getTopSellers_shouldReturnClampedResults() {
        TopSellerProjection sellerProj = mock(TopSellerProjection.class);
        when(sellerProj.getSellerId()).thenReturn(1L);
        when(sellerProj.getSellerName()).thenReturn("Top Seller");
        when(sellerProj.getTotalOrders()).thenReturn(20L);
        when(sellerProj.getGrossSales()).thenReturn(new BigDecimal("50000000.00"));
        when(sellerProj.getCommissionPaid()).thenReturn(new BigDecimal("2500000.00"));
        when(sellerProj.getSellerNetAmount()).thenReturn(new BigDecimal("47500000.00"));

        when(settlementRepository.getTopSellers(any(OffsetDateTime.class), any(OffsetDateTime.class), eq(50)))
                .thenReturn(List.of(sellerProj));

        List<TopSellerResponse> result = adminAnalyticsService.getTopSellers("30d", null, null, 100);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sellerName()).isEqualTo("Top Seller");
    }

    @Test
    void getCategoryBreakdown_shouldComputePercentageShare() {
        CategoryGmvProjection cat1 = mock(CategoryGmvProjection.class);
        when(cat1.getCategoryId()).thenReturn(1L);
        when(cat1.getCategoryName()).thenReturn("Laptops");
        when(cat1.getTotalUnitsSold()).thenReturn(10L);
        when(cat1.getGmv()).thenReturn(new BigDecimal("75000000.00"));
        when(cat1.getPlatformCommissionEarned()).thenReturn(new BigDecimal("3750000.00"));

        CategoryGmvProjection cat2 = mock(CategoryGmvProjection.class);
        when(cat2.getCategoryId()).thenReturn(2L);
        when(cat2.getCategoryName()).thenReturn("Mice");
        when(cat2.getTotalUnitsSold()).thenReturn(20L);
        when(cat2.getGmv()).thenReturn(new BigDecimal("25000000.00"));
        when(cat2.getPlatformCommissionEarned()).thenReturn(new BigDecimal("2000000.00"));

        when(settlementRepository.getCategoryBreakdown(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(cat1, cat2));

        List<CategoryGmvResponse> breakdown = adminAnalyticsService.getCategoryBreakdown("7d", null, null);

        assertThat(breakdown).hasSize(2);
        assertThat(breakdown.get(0).percentageShare()).isEqualByComparingTo("75.00");
        assertThat(breakdown.get(1).percentageShare()).isEqualByComparingTo("25.00");
    }

    @Test
    void getRevenueChart_shouldReturnTimeline() {
        DailyRevenueProjection point = mock(DailyRevenueProjection.class);
        when(point.getDateStr()).thenReturn("2026-10-15");
        when(point.getGmv()).thenReturn(new BigDecimal("10000000.00"));
        when(point.getCommission()).thenReturn(new BigDecimal("500000.00"));
        when(point.getOrderCount()).thenReturn(5L);

        when(settlementRepository.getDailyRevenueTimeline(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(point));

        PlatformRevenueChartResponse response = adminAnalyticsService.getRevenueChart("7d", null, null, "daily");

        assertThat(response.interval()).isEqualTo("daily");
        assertThat(response.timeline()).hasSize(1);
        assertThat(response.timeline().get(0).date()).isEqualTo("2026-10-15");
    }
}
