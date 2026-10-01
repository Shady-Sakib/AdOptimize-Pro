package com.adoptimizer.dto.response;

import java.util.List;

public record CampaignDetailResponse(
        CampaignResponse campaign,
        TrendResponse dailyTrend,
        List<OptimizationLogResponse> optimizations) {
}
