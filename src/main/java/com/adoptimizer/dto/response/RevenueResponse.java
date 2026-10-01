package com.adoptimizer.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record RevenueResponse(
        BigDecimal totalRevenue,
        BigDecimal platformEarnings,
        BigDecimal platformFee,
        long transactionCount,
        MoneyTrendResponse monthly,
        List<PaymentResponse> payments) {
}
