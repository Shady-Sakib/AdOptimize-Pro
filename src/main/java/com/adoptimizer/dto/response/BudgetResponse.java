package com.adoptimizer.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record BudgetResponse(
        WalletResponse wallet,
        BigDecimal totalBudgeted,
        BigDecimal totalSpent,
        BigDecimal remaining,
        int usedPercent,
        List<CampaignResponse> campaigns,
        TrendResponse monthlySpend) {
}
