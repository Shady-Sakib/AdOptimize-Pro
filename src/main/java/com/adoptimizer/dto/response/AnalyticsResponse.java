package com.adoptimizer.dto.response;

import java.util.List;

public record AnalyticsResponse(
        long impressions,
        long clicks,
        long conversions,
        double ctr,
        TrendResponse monthlyTrend,
        List<AudienceShareResponse> audiences,
        MoneyTrendResponse revenueVsSpend) {
}
