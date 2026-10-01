package com.adoptimizer.dto.response;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Small rounding helpers used when building responses. */
public final class Numbers {

    private Numbers() {
    }

    public static double round2(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Percentage change from previous to current, or {@code null} when there is no previous value. */
    public static Double percentChange(double current, double previous) {
        if (previous <= 0.0) {
            return null;
        }
        return round2(((current - previous) / previous) * 100.0);
    }
}
