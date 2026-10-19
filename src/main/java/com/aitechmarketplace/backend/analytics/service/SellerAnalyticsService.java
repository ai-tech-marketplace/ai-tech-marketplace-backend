package com.aitechmarketplace.backend.analytics.service;

import com.aitechmarketplace.backend.analytics.dto.seller.SellerAnalyticsOverviewResponse;
import com.aitechmarketplace.backend.analytics.dto.seller.SellerSettlementResponse;
import com.aitechmarketplace.backend.analytics.dto.seller.TopSellerProductResponse;
import com.aitechmarketplace.backend.settlement.entity.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SellerAnalyticsService {

    SellerAnalyticsOverviewResponse getOverview(Long sellerId, String period, String startDate, String endDate);

    List<TopSellerProductResponse> getTopProducts(Long sellerId, String period, String startDate, String endDate, Integer limit);

    Page<SellerSettlementResponse> getSettlementLedger(Long sellerId, SettlementStatus status, Pageable pageable);
}
