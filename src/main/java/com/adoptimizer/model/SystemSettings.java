package com.adoptimizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** Platform-wide configuration managed from the admin Settings page (single database row). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSettings {

    private BigDecimal cpcRate;
    private BigDecimal cpmRate;
    private BigDecimal minBudget;
    private BigDecimal maxDailyBudget;
    private BigDecimal platformFee;
    private int peakHoursStart;
    private int peakHoursEnd;
    private boolean autoApprove;
    private boolean contentFilter;
    private boolean budgetAlerts;
    private boolean simulationEnabled;
    private String bannedWords;
    private LocalDateTime updatedAt;

    public List<String> bannedWordList() {
        if (bannedWords == null || bannedWords.isBlank()) {
            return List.of();
        }
        return Arrays.stream(bannedWords.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(word -> !word.isEmpty())
                .distinct()
                .toList();
    }
}
