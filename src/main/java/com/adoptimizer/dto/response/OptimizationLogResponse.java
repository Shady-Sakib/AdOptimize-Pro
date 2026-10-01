package com.adoptimizer.dto.response;

import com.adoptimizer.model.OptimizationLog;

import java.time.LocalDateTime;

public record OptimizationLogResponse(
        String type,
        String description,
        LocalDateTime appliedAt,
        String campaignTitle,
        String userName) {

    public static OptimizationLogResponse from(OptimizationLog log) {
        return new OptimizationLogResponse(log.getType().getCode(), log.getDescription(), log.getAppliedAt(),
                log.getCampaignTitle(), log.getUserName());
    }
}
