package com.aitechmarketplace.backend.analytics.service;

import com.aitechmarketplace.backend.analytics.dto.customer.CategorySpendingResponse;
import com.aitechmarketplace.backend.analytics.dto.customer.CustomerSpendingSummaryResponse;
import com.aitechmarketplace.backend.analytics.dto.customer.MonthlySpendingResponse;

import java.util.List;

public interface CustomerAnalyticsService {

    CustomerSpendingSummaryResponse getSpendingSummary(Long userId);

    List<CategorySpendingResponse> getCategoryBreakdown(Long userId);

    MonthlySpendingResponse getMonthlySpending(Long userId, Integer months);
}
