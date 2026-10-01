package com.adoptimizer.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Time series for charts; every list has the same length as {@code labels}. */
public record TrendResponse(
        List<String> labels,
        List<Long> impressions,
        List<Long> clicks,
        List<Long> conversions,
        List<BigDecimal> spend) {
}
