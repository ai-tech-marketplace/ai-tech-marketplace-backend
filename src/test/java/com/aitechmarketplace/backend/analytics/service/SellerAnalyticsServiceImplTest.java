package com.aitechmarketplace.backend.analytics.service;

import com.aitechmarketplace.backend.analytics.dto.seller.SellerAnalyticsOverviewResponse;
import com.aitechmarketplace.backend.analytics.dto.seller.SellerSettlementResponse;
import com.aitechmarketplace.backend.analytics.dto.seller.TopSellerProductResponse;
import com.aitechmarketplace.backend.analytics.repository.SellerOverviewProjection;
import com.aitechmarketplace.backend.analytics.repository.SellerSettlementItemProjection;
import com.aitechmarketplace.backend.analytics.repository.TopSellerProductProjection;
import com.aitechmarketplace.backend.settlement.entity.SettlementStatus;
import com.aitechmarketplace.backend.settlement.repository.SellerOrderSettlementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerAnalyticsServiceImplTest {

    @Mock
    private SellerOrderSettlementRepository settlementRepository;

    @InjectMocks
    private SellerAnalyticsServiceImpl sellerAnalyticsService;

    @Test
    void getOverview_shouldReturnSellerMetrics() {
        SellerOverviewProjection overviewProj = mock(SellerOverviewProjection.class);
        when(overviewProj.getGrossSales()).thenReturn(new BigDecimal("10000000.00"));
        when(overviewProj.getCommissionPaid()).thenReturn(new BigDecimal("500000.00"));
        when(overviewProj.getNetEarnings()).thenReturn(new BigDecimal("9500000.00"));
        when(overviewProj.getTotalOrders()).thenReturn(3L);
        when(overviewProj.getTotalUnitsSold()).thenReturn(5L);

        when(settlementRepository.getSellerOverview(eq(10L), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(overviewProj);
        when(settlementRepository.getSellerBalanceByStatus(10L, "PENDING"))
                .thenReturn(new BigDecimal("4000000.00"));
        when(settlementRepository.getSellerBalanceByStatus(10L, "SETTLED"))
                .thenReturn(new BigDecimal("5500000.00"));

        SellerAnalyticsOverviewResponse response = sellerAnalyticsService.getOverview(10L, "30d", null, null);

        assertThat(response.currency()).isEqualTo("VND");
        assertThat(response.grossSales()).isEqualByComparingTo("10000000.00");
        assertThat(response.commissionPaid()).isEqualByComparingTo("500000.00");
        assertThat(response.netEarnings()).isEqualByComparingTo("9500000.00");
        assertThat(response.pendingSettlement()).isEqualByComparingTo("4000000.00");
        assertThat(response.availableBalance()).isEqualByComparingTo("5500000.00");
        assertThat(response.totalOrders()).isEqualTo(3L);
        assertThat(response.totalUnitsSold()).isEqualTo(5L);
    }

    @Test
    void getTopProducts_shouldReturnRankedProducts() {
        TopSellerProductProjection prodProj = mock(TopSellerProductProjection.class);
        when(prodProj.getProductId()).thenReturn(101L);
        when(prodProj.getProductName()).thenReturn("Gaming Mouse");
        when(prodProj.getSku()).thenReturn("MOUSE-001");
        when(prodProj.getUnitsSold()).thenReturn(10L);
        when(prodProj.getGrossRevenue()).thenReturn(new BigDecimal("5000000.00"));
        when(prodProj.getNetRevenue()).thenReturn(new BigDecimal("4750000.00"));

        when(settlementRepository.getTopSellerProducts(eq(10L), any(OffsetDateTime.class), any(OffsetDateTime.class), eq(5)))
                .thenReturn(List.of(prodProj));

        List<TopSellerProductResponse> result = sellerAnalyticsService.getTopProducts(10L, "7d", null, null, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productName()).isEqualTo("Gaming Mouse");
        assertThat(result.get(0).grossRevenue()).isEqualByComparingTo("5000000.00");
    }

    @Test
    void getSettlementLedger_shouldReturnPagedSettlements() {
        SellerSettlementItemProjection itemProj = mock(SellerSettlementItemProjection.class);
        when(itemProj.getSettlementId()).thenReturn(501L);
        when(itemProj.getOrderNumber()).thenReturn("ORD-12345");
        when(itemProj.getProductName()).thenReturn("Keyboard");
        when(itemProj.getQuantity()).thenReturn(1);
        when(itemProj.getItemSubtotal()).thenReturn(new BigDecimal("2000000.00"));
        when(itemProj.getCommissionRate()).thenReturn(new BigDecimal("0.0500"));
        when(itemProj.getCommissionAmount()).thenReturn(new BigDecimal("100000.00"));
        when(itemProj.getPlatformDiscountSubsidized()).thenReturn(BigDecimal.ZERO);
        when(itemProj.getSellerNetAmount()).thenReturn(new BigDecimal("1900000.00"));
        when(itemProj.getStatus()).thenReturn(SettlementStatus.SETTLED);
        when(itemProj.getSettledAt()).thenReturn(java.time.Instant.now());
        when(itemProj.getCreatedAt()).thenReturn(java.time.Instant.now());

        PageRequest pageRequest = PageRequest.of(0, 10);
        when(settlementRepository.getSellerSettlementLedger(10L, "SETTLED", pageRequest))
                .thenReturn(new PageImpl<>(List.of(itemProj), pageRequest, 1));

        Page<SellerSettlementResponse> page = sellerAnalyticsService.getSettlementLedger(10L, SettlementStatus.SETTLED, pageRequest);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).orderNumber()).isEqualTo("ORD-12345");
        assertThat(page.getContent().get(0).sellerNetAmount()).isEqualByComparingTo("1900000.00");
    }
}
