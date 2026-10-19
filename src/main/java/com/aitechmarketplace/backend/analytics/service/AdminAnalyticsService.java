package com.aitechmarketplace.backend.analytics.service;

import com.aitechmarketplace.backend.analytics.dto.admin.CategoryGmvResponse;
import com.aitechmarketplace.backend.analytics.dto.admin.PlatformAnalyticsOverviewResponse;
import com.aitechmarketplace.backend.analytics.dto.admin.PlatformRevenueChartResponse;
import com.aitechmarketplace.backend.analytics.dto.admin.TopSellerResponse;

import java.util.List;

public interface AdminAnalyticsService {

    PlatformAnalyticsOverviewResponse getOverview(String period, String startDate, String endDate);

    List<TopSellerResponse> getTopSellers(String period, String startDate, String endDate, Integer limit);

    List<CategoryGmvResponse> getCategoryBreakdown(String period, String startDate, String endDate);

    PlatformRevenueChartResponse getRevenueChart(String period, String startDate, String endDate, String interval);
}
