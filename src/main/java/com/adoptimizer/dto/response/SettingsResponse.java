package com.adoptimizer.dto.response;

import com.adoptimizer.model.SystemSettings;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettingsResponse(
        BigDecimal cpcRate,
        BigDecimal cpmRate,
        BigDecimal minBudget,
        BigDecimal maxDailyBudget,
        BigDecimal platformFee,
        int peakHoursStart,
        int peakHoursEnd,
        boolean autoApprove,
        boolean contentFilter,
        boolean budgetAlerts,
        boolean simulationEnabled,
        String bannedWords,
        LocalDateTime updatedAt) {

    public static SettingsResponse from(SystemSettings s) {
        return new SettingsResponse(Numbers.money(s.getCpcRate()), Numbers.money(s.getCpmRate()),
                Numbers.money(s.getMinBudget()), Numbers.money(s.getMaxDailyBudget()), Numbers.money(s.getPlatformFee()),
                s.getPeakHoursStart(), s.getPeakHoursEnd(), s.isAutoApprove(), s.isContentFilter(),
                s.isBudgetAlerts(), s.isSimulationEnabled(), s.getBannedWords(), s.getUpdatedAt());
    }
}
