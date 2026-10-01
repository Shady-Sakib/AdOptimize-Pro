package com.adoptimizer.repository;

import com.adoptimizer.model.SystemSettings;
import com.adoptimizer.util.SqlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SettingsRepository {

    private final JdbcClient jdbc;

    public SystemSettings get() {
        return jdbc.sql("SELECT * FROM system_settings WHERE id = 1")
                .query((rs, rowNum) -> SystemSettings.builder()
                        .cpcRate(rs.getBigDecimal("cpc_rate"))
                        .cpmRate(rs.getBigDecimal("cpm_rate"))
                        .minBudget(rs.getBigDecimal("min_budget"))
                        .maxDailyBudget(rs.getBigDecimal("max_daily_budget"))
                        .platformFee(rs.getBigDecimal("platform_fee"))
                        .peakHoursStart(rs.getInt("peak_hours_start"))
                        .peakHoursEnd(rs.getInt("peak_hours_end"))
                        .autoApprove(rs.getBoolean("auto_approve"))
                        .contentFilter(rs.getBoolean("content_filter"))
                        .budgetAlerts(rs.getBoolean("budget_alerts"))
                        .simulationEnabled(rs.getBoolean("simulation_enabled"))
                        .bannedWords(rs.getString("banned_words"))
                        .updatedAt(SqlUtils.dateTime(rs, "updated_at"))
                        .build())
                .single();
    }

    public void update(SystemSettings s) {
        jdbc.sql("""
                UPDATE system_settings SET cpc_rate = :cpc, cpm_rate = :cpm, min_budget = :minBudget,
                    max_daily_budget = :maxDaily, platform_fee = :fee, peak_hours_start = :peakStart,
                    peak_hours_end = :peakEnd, auto_approve = :autoApprove, content_filter = :contentFilter,
                    budget_alerts = :budgetAlerts, simulation_enabled = :simulation, banned_words = :bannedWords,
                    updated_at = :updatedAt
                WHERE id = 1
                """)
                .param("cpc", s.getCpcRate())
                .param("cpm", s.getCpmRate())
                .param("minBudget", s.getMinBudget())
                .param("maxDaily", s.getMaxDailyBudget())
                .param("fee", s.getPlatformFee())
                .param("peakStart", s.getPeakHoursStart())
                .param("peakEnd", s.getPeakHoursEnd())
                .param("autoApprove", s.isAutoApprove())
                .param("contentFilter", s.isContentFilter())
                .param("budgetAlerts", s.isBudgetAlerts())
                .param("simulation", s.isSimulationEnabled())
                .param("bannedWords", s.getBannedWords())
                .param("updatedAt", s.getUpdatedAt())
                .update();
    }
}
