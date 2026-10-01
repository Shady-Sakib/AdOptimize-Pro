package com.adoptimizer.dto.response;

import java.math.BigDecimal;

/** Totals for a period plus percentage change against the previous period of equal length ({@code null} = no data). */
public record KpiResponse(
        long impressions,
        long clicks,
        long conversions,
        BigDecimal spend,
        double ctr,
        Double impressionsChange,
        Double clicksChange,
        Double ctrChange,
        Double spendChange) {
}
