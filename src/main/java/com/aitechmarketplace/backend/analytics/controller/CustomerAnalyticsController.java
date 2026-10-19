package com.aitechmarketplace.backend.analytics.controller;

import com.aitechmarketplace.backend.analytics.dto.customer.CategorySpendingResponse;
import com.aitechmarketplace.backend.analytics.dto.customer.CustomerSpendingSummaryResponse;
import com.aitechmarketplace.backend.analytics.dto.customer.MonthlySpendingResponse;
import com.aitechmarketplace.backend.analytics.service.CustomerAnalyticsService;
import com.aitechmarketplace.backend.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer/analytics")
@PreAuthorize("isAuthenticated()")
public class CustomerAnalyticsController {

    private final CustomerAnalyticsService customerAnalyticsService;

    public CustomerAnalyticsController(CustomerAnalyticsService customerAnalyticsService) {
        this.customerAnalyticsService = customerAnalyticsService;
    }

    @GetMapping("/spending-summary")
    public ResponseEntity<ApiResponse<CustomerSpendingSummaryResponse>> getSpendingSummary(
            @RequestAttribute("userId") Long userId
    ) {
        CustomerSpendingSummaryResponse response = customerAnalyticsService.getSpendingSummary(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/category-breakdown")
    public ResponseEntity<ApiResponse<List<CategorySpendingResponse>>> getCategoryBreakdown(
            @RequestAttribute("userId") Long userId
    ) {
        List<CategorySpendingResponse> response = customerAnalyticsService.getCategoryBreakdown(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/monthly-spending")
    public ResponseEntity<ApiResponse<MonthlySpendingResponse>> getMonthlySpending(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false, defaultValue = "6") Integer months
    ) {
        MonthlySpendingResponse response = customerAnalyticsService.getMonthlySpending(userId, months);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
