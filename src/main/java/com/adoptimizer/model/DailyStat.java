package com.adoptimizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyStat {

    private Long campaignId;
    private LocalDate statDate;
    private long impressions;
    private long clicks;
    private long conversions;
    @Builder.Default
    private BigDecimal spend = BigDecimal.ZERO;
}
