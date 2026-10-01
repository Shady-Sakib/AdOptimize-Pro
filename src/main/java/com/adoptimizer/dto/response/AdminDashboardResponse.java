package com.adoptimizer.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AdminDashboardResponse(
        long totalUsers,
        long activeUsers,
        long totalCampaigns,
        long activeCampaigns,
        long pendingApprovals,
        long openTickets,
        BigDecimal totalRevenue,
        TrendResponse impressionsTrend,
        MoneyTrendResponse revenueTrend,
        List<CampaignResponse> pendingCampaigns,
        List<TicketResponse> recentOpenTickets) {
}
