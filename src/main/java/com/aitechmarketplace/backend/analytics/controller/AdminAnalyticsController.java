package com.aitechmarketplace.backend.analytics.controller;

import com.aitechmarketplace.backend.analytics.dto.admin.CategoryGmvResponse;
import com.aitechmarketplace.backend.analytics.dto.admin.PlatformAnalyticsOverviewResponse;
import com.aitechmarketplace.backend.analytics.dto.admin.PlatformRevenueChartResponse;
import com.aitechmarketplace.backend.analytics.dto.admin.TopSellerResponse;
import com.aitechmarketplace.backend.analytics.service.AdminAnalyticsService;
import com.aitechmarketplace.backend.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    public AdminAnalyticsController(AdminAnalyticsService adminAnalyticsService) {
        this.adminAnalyticsService = adminAnalyticsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<PlatformAnalyticsOverviewResponse>> getOverview(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        PlatformAnalyticsOverviewResponse response = adminAnalyticsService.getOverview(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/top-sellers")
    public ResponseEntity<ApiResponse<List<TopSellerResponse>>> getTopSellers(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "10") Integer limit
    ) {
        List<TopSellerResponse> response = adminAnalyticsService.getTopSellers(period, startDate, endDate, limit);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/top-categories")
    public ResponseEntity<ApiResponse<List<CategoryGmvResponse>>> getTopCategories(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        List<CategoryGmvResponse> response = adminAnalyticsService.getCategoryBreakdown(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/revenue-chart")
    public ResponseEntity<ApiResponse<PlatformRevenueChartResponse>> getRevenueChart(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "daily") String interval
    ) {
        PlatformRevenueChartResponse response = adminAnalyticsService.getRevenueChart(period, startDate, endDate, interval);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
