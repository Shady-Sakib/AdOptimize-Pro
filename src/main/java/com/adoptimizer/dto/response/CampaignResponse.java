package com.adoptimizer.dto.response;

import com.adoptimizer.model.Campaign;
import com.adoptimizer.model.CampaignStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CampaignResponse(
        Long id,
        String title,
        String description,
        String audience,
        String audienceLabel,
        String adType,
        String adTypeLabel,
        BigDecimal budget,
        BigDecimal dailyBudget,
        BigDecimal spent,
        BigDecimal remaining,
        int budgetUsedPercent,
        LocalDate startDate,
        LocalDate endDate,
        List<String> keywords,
        String status,
        String rejectionReason,
        boolean flagged,
        String flagReason,
        boolean peakHoursOnly,
        long impressions,
        long clicks,
        long conversions,
        double ctr,
        double conversionRate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime reviewedAt,
        Long ownerId,
        String ownerName,
        String ownerEmail,
        boolean editable,
        boolean pausable,
        boolean resumable) {

    public static CampaignResponse from(Campaign c) {
        CampaignStatus status = c.getStatus();
        return new CampaignResponse(
                c.getId(),
                c.getTitle(),
                c.getDescription(),
                c.getAudience().getCode(),
                c.getAudience().getLabel(),
                c.getAdType().getCode(),
                c.getAdType().getLabel(),
                Numbers.money(c.getBudget()),
                Numbers.money(c.getDailyBudget()),
                Numbers.money(c.getSpent()),
                Numbers.money(c.remainingBudget()),
                c.budgetUsedPercent(),
                c.getStartDate(),
                c.getEndDate(),
                List.copyOf(c.getKeywords()),
                status.getCode(),
                c.getRejectionReason(),
                c.isFlagOpen(),
                c.getFlagReason(),
                c.isPeakHoursOnly(),
                c.getImpressions(),
                c.getClicks(),
                c.getConversions(),
                Numbers.round2(c.ctr()),
                Numbers.round2(c.conversionRate()),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getReviewedAt(),
                c.getUserId(),
                c.getOwnerName(),
                c.getOwnerEmail(),
                status != CampaignStatus.COMPLETED,
                status == CampaignStatus.ACTIVE || status == CampaignStatus.SCHEDULED,
                status == CampaignStatus.PAUSED);
    }
}
