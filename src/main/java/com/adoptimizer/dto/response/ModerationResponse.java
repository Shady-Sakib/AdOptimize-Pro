package com.adoptimizer.dto.response;

import java.util.List;

public record ModerationResponse(
        long flaggedOpen,
        long clean,
        long reviewedToday,
        long autoFlaggedTotal,
        boolean contentFilterEnabled,
        List<String> bannedWords,
        List<CampaignResponse> flaggedCampaigns) {
}
