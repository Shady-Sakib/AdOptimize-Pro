package com.adoptimizer.dto.response;

public record ReportSummaryResponse(
        long totalUsers,
        long activeCampaigns,
        long pendingReviews,
        long totalCampaigns,
        long totalPayments,
        long openTickets,
        long optimizationsApplied) {
}
