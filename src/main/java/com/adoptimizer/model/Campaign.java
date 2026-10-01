package com.adoptimizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Campaign {

    private Long id;
    private Long userId;
    private String title;
    private String description;
    private Audience audience;
    private AdType adType;
    private BigDecimal budget;
    private BigDecimal dailyBudget;
    @Builder.Default
    private BigDecimal spent = BigDecimal.ZERO;
    private LocalDate startDate;
    private LocalDate endDate;
    @Builder.Default
    private List<String> keywords = new ArrayList<>();
    private CampaignStatus status;
    private String rejectionReason;
    private boolean flagged;
    private String flagReason;
    private boolean flagCleared;
    private boolean peakHoursOnly;
    @Builder.Default
    private BigDecimal qualityScore = BigDecimal.ONE;
    private long impressions;
    private long clicks;
    private long conversions;
    private boolean budgetAlertSent;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    /** Populated only by queries that join the owner (admin views). */
    private String ownerName;
    private String ownerEmail;

    public BigDecimal remainingBudget() {
        return budget.subtract(spent).max(BigDecimal.ZERO);
    }

    /** Click-through rate in percent. */
    public double ctr() {
        return impressions == 0 ? 0.0 : (clicks * 100.0) / impressions;
    }

    /** Conversion rate (conversions / clicks) in percent. */
    public double conversionRate() {
        return clicks == 0 ? 0.0 : (conversions * 100.0) / clicks;
    }

    /** Share of the total budget already spent, 0..100. */
    public int budgetUsedPercent() {
        if (budget == null || budget.signum() == 0) {
            return 0;
        }
        return spent.multiply(BigDecimal.valueOf(100)).divide(budget, 0, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100)).intValue();
    }

    /** A campaign is "flagged for review" when the content filter matched and no admin cleared it yet. */
    public boolean isFlagOpen() {
        return flagged && !flagCleared;
    }
}
