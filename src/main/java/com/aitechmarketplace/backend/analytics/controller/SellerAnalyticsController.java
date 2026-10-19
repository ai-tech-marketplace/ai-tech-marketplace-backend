package com.aitechmarketplace.backend.analytics.controller;

import com.aitechmarketplace.backend.analytics.dto.seller.SellerAnalyticsOverviewResponse;
import com.aitechmarketplace.backend.analytics.dto.seller.SellerSettlementResponse;
import com.aitechmarketplace.backend.analytics.dto.seller.TopSellerProductResponse;
import com.aitechmarketplace.backend.analytics.service.SellerAnalyticsService;
import com.aitechmarketplace.backend.common.dto.ApiResponse;
import com.aitechmarketplace.backend.settlement.entity.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/seller/analytics")
@PreAuthorize("hasRole('SELLER')")
public class SellerAnalyticsController {

    private final SellerAnalyticsService sellerAnalyticsService;

    public SellerAnalyticsController(SellerAnalyticsService sellerAnalyticsService) {
        this.sellerAnalyticsService = sellerAnalyticsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<SellerAnalyticsOverviewResponse>> getOverview(
            @RequestAttribute("userId") Long sellerId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        SellerAnalyticsOverviewResponse response = sellerAnalyticsService.getOverview(sellerId, period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/top-products")
    public ResponseEntity<ApiResponse<List<TopSellerProductResponse>>> getTopProducts(
            @RequestAttribute("userId") Long sellerId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "10") Integer limit
    ) {
        List<TopSellerProductResponse> response = sellerAnalyticsService.getTopProducts(sellerId, period, startDate, endDate, limit);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/order-settlements")
    public ResponseEntity<ApiResponse<Page<SellerSettlementResponse>>> getOrderSettlements(
            @RequestAttribute("userId") Long sellerId,
            @RequestParam(required = false) SettlementStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SellerSettlementResponse> response = sellerAnalyticsService.getSettlementLedger(sellerId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
