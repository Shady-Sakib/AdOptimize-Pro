package com.adoptimizer.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Everything the campaign form needs: select options and the admin-configured limits. */
public record CampaignOptionsResponse(
        List<OptionResponse> audiences,
        List<OptionResponse> adTypes,
        BigDecimal minBudget,
        BigDecimal maxDailyBudget,
        BigDecimal availableFunds) {
}
