package com.adoptimizer.dto.response;

import java.util.List;
import java.util.Map;

public record OverviewResponse(
        int days,
        KpiResponse kpis,
        TrendResponse trend,
        Map<String, Long> statusCounts,
        List<CampaignResponse> recentCampaigns,
        WalletResponse wallet) {
}
