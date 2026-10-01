package com.adoptimizer.service;

import com.adoptimizer.dto.request.SettingsRequest;
import com.adoptimizer.model.SystemSettings;
import com.adoptimizer.repository.SettingsRepository;
import com.adoptimizer.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsRepository settingsRepository;

    public SystemSettings get() {
        return settingsRepository.get();
    }

    @Transactional
    public SystemSettings update(SettingsRequest request) {
        SystemSettings settings = SystemSettings.builder()
                .cpcRate(request.getCpcRate().setScale(2, RoundingMode.HALF_UP))
                .cpmRate(request.getCpmRate().setScale(2, RoundingMode.HALF_UP))
                .minBudget(request.getMinBudget().setScale(2, RoundingMode.HALF_UP))
                .maxDailyBudget(request.getMaxDailyBudget().setScale(2, RoundingMode.HALF_UP))
                .platformFee(request.getPlatformFee().setScale(2, RoundingMode.HALF_UP))
                .peakHoursStart(request.getPeakHoursStart())
                .peakHoursEnd(request.getPeakHoursEnd())
                .autoApprove(request.getAutoApprove())
                .contentFilter(request.getContentFilter())
                .budgetAlerts(request.getBudgetAlerts())
                .simulationEnabled(request.getSimulationEnabled())
                .bannedWords(normalizeWords(request.getBannedWords()))
                .updatedAt(TimeUtils.now())
                .build();
        settingsRepository.update(settings);
        return settings;
    }

    /** Trims, lower-cases and de-duplicates the comma-separated blocked word list. */
    static String normalizeWords(String raw) {
        if (raw == null) {
            return "";
        }
        return Arrays.stream(raw.split("[,\\n]"))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(word -> !word.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
    }
}
