package com.adoptimizer.dto.response;

import java.util.List;

public record OptimizationResponse(
        CampaignResponse campaign,
        int score,
        String scoreLabel,
        boolean enoughData,
        double benchmarkCtr,
        double benchmarkConversionRate,
        double pacingPercent,
        List<SuggestionResponse> suggestions,
        List<OptimizationLogResponse> history) {
}
