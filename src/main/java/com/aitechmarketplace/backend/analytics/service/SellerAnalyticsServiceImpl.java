package com.aitechmarketplace.backend.analytics.service;

import com.aitechmarketplace.backend.analytics.dto.seller.SellerAnalyticsOverviewResponse;
import com.aitechmarketplace.backend.analytics.dto.seller.SellerSettlementResponse;
import com.aitechmarketplace.backend.analytics.dto.seller.TopSellerProductResponse;
import com.aitechmarketplace.backend.analytics.repository.SellerOverviewProjection;
import com.aitechmarketplace.backend.analytics.repository.SellerSettlementItemProjection;
import com.aitechmarketplace.backend.analytics.repository.TopSellerProductProjection;
import com.aitechmarketplace.backend.analytics.util.DateRangeParser;
import com.aitechmarketplace.backend.settlement.entity.SettlementStatus;
import com.aitechmarketplace.backend.settlement.repository.SellerOrderSettlementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SellerAnalyticsServiceImpl implements SellerAnalyticsService {

    private final SellerOrderSettlementRepository settlementRepository;

    public SellerAnalyticsServiceImpl(SellerOrderSettlementRepository settlementRepository) {
        this.settlementRepository = settlementRepository;
    }

    @Override
    public SellerAnalyticsOverviewResponse getOverview(Long sellerId, String period, String startDate, String endDate) {
        DateRangeParser.DateRange range = DateRangeParser.parse(period, startDate, endDate);

        SellerOverviewProjection overview = settlementRepository.getSellerOverview(sellerId, range.start(), range.end());

        BigDecimal grossSales = (overview != null && overview.getGrossSales() != null)
                ? overview.getGrossSales()
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal commissionPaid = (overview != null && overview.getCommissionPaid() != null)
                ? overview.getCommissionPaid()
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal netEarnings = (overview != null && overview.getNetEarnings() != null)
                ? overview.getNetEarnings()
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        long totalOrders = (overview != null && overview.getTotalOrders() != null)
                ? overview.getTotalOrders()
                : 0L;

        long totalUnitsSold = (overview != null && overview.getTotalUnitsSold() != null)
                ? overview.getTotalUnitsSold()
                : 0L;

        BigDecimal pendingSettlement = settlementRepository.getSellerBalanceByStatus(sellerId, "PENDING");
        if (pendingSettlement == null) {
            pendingSettlement = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal availableBalance = settlementRepository.getSellerBalanceByStatus(sellerId, "SETTLED");
        if (availableBalance == null) {
            availableBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return new SellerAnalyticsOverviewResponse(
                "VND",
                range.start(),
                range.end(),
                grossSales,
                commissionPaid,
                netEarnings,
                pendingSettlement,
                availableBalance,
                totalOrders,
                totalUnitsSold
        );
    }

    @Override
    public List<TopSellerProductResponse> getTopProducts(Long sellerId, String period, String startDate, String endDate, Integer limit) {
        DateRangeParser.DateRange range = DateRangeParser.parse(period, startDate, endDate);
        int effectiveLimit = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);

        List<TopSellerProductProjection> projections = settlementRepository.getTopSellerProducts(
                sellerId, range.start(), range.end(), effectiveLimit
        );

        return projections.stream()
                .map(p -> new TopSellerProductResponse(
                        p.getProductId(),
                        p.getProductName(),
                        p.getSku(),
                        p.getUnitsSold() != null ? p.getUnitsSold() : 0L,
                        p.getGrossRevenue() != null ? p.getGrossRevenue() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        p.getNetRevenue() != null ? p.getNetRevenue() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                ))
                .toList();
    }

    @Override
    public Page<SellerSettlementResponse> getSettlementLedger(Long sellerId, SettlementStatus status, Pageable pageable) {
        String statusStr = status != null ? status.name() : null;
        Page<SellerSettlementItemProjection> projections = settlementRepository.getSellerSettlementLedger(sellerId, statusStr, pageable);

        return projections.map(p -> new SellerSettlementResponse(
                p.getSettlementId(),
                p.getOrderNumber(),
                p.getProductName(),
                p.getQuantity() != null ? p.getQuantity() : 0,
                p.getItemSubtotal(),
                p.getCommissionRate(),
                p.getCommissionAmount(),
                p.getPlatformDiscountSubsidized(),
                p.getSellerNetAmount(),
                p.getStatus(),
                p.getSettledAt() != null ? p.getSettledAt().atOffset(java.time.ZoneOffset.UTC) : null,
                p.getCreatedAt() != null ? p.getCreatedAt().atOffset(java.time.ZoneOffset.UTC) : null
        ));
    }
}
