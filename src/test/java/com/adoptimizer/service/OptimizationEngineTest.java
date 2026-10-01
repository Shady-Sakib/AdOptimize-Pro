package com.adoptimizer.service;

import com.adoptimizer.dto.response.SuggestionResponse;
import com.adoptimizer.model.AdType;
import com.adoptimizer.model.Audience;
import com.adoptimizer.model.Campaign;
import com.adoptimizer.model.CampaignStatus;
import com.adoptimizer.model.OptimizationType;
import com.adoptimizer.model.SystemSettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizationEngineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 17);

    private final OptimizationEngine engine = new OptimizationEngine();

    private static SystemSettings settings() {
        return SystemSettings.builder()
                .cpcRate(new BigDecimal("0.45")).cpmRate(new BigDecimal("2.50"))
                .minBudget(new BigDecimal("50")).maxDailyBudget(new BigDecimal("10000"))
                .platformFee(new BigDecimal("5")).peakHoursStart(18).peakHoursEnd(22)
                .contentFilter(true).budgetAlerts(true).simulationEnabled(true).bannedWords("")
                .build();
    }

    private static Campaign.CampaignBuilder campaign() {
        return Campaign.builder()
                .id(1L).userId(2L).title("Test").description("Test campaign")
                .audience(Audience.MILLENNIALS).adType(AdType.VIDEO)
                .budget(new BigDecimal("900")).dailyBudget(new BigDecimal("30")).spent(new BigDecimal("300"))
                .startDate(TODAY.minusDays(20)).endDate(TODAY.plusDays(19))
                .keywords(new ArrayList<>(List.of("lifestyle", "deals", "travel", "online shopping", "subscription")))
                .status(CampaignStatus.ACTIVE).peakHoursOnly(true).qualityScore(BigDecimal.ONE);
    }

    private static Set<String> types(OptimizationEngine.Analysis analysis) {
        return analysis.suggestions().stream().map(SuggestionResponse::type).collect(Collectors.toSet());
    }

    @Test
    void wellConfiguredCampaignHasNoSuggestionsAndAHighScore() {
        // CTR and conversion rate above the millennial benchmarks (2.10% / 3.20%); budget evenly paced.
        Campaign c = campaign().impressions(50_000).clicks(1_300).conversions(52).build();

        OptimizationEngine.Analysis analysis = engine.analyze(c, settings(), TODAY);

        assertTrue(analysis.suggestions().isEmpty(), "unexpected: " + types(analysis));
        assertTrue(analysis.score() >= 80, "score was " + analysis.score());
        assertEquals("Excellent", analysis.scoreLabel());
    }

    @Test
    void weakSetupProducesApplicableSuggestions() {
        Campaign c = campaign()
                .adType(AdType.IMAGE)
                .keywords(new ArrayList<>(List.of("fashion")))
                .peakHoursOnly(false)
                .impressions(40_000).clicks(600).conversions(10)
                .build();

        OptimizationEngine.Analysis analysis = engine.analyze(c, settings(), TODAY);

        assertEquals(Set.of("keywords", "ad-format", "schedule", "conversion"), types(analysis));
        assertTrue(analysis.applicable(OptimizationType.KEYWORDS).isPresent());
        assertTrue(analysis.applicable(OptimizationType.CONVERSION).isEmpty(), "conversion advice is not auto-applicable");
        assertEquals("high", analysis.suggestions().get(0).priority(), "high-impact suggestions come first");
        // 19 (CTR 71% of benchmark) + 10 (conversion 52%) + 20 (evenly paced) + 0 (setup) = 49
        assertEquals(49, analysis.score());
        assertEquals("Needs attention", analysis.scoreLabel());
    }

    @Test
    void notEnoughDataUsesNeutralScoresAndNoConversionAdvice() {
        Campaign c = campaign().impressions(100).clicks(2).conversions(0).build();

        OptimizationEngine.Analysis analysis = engine.analyze(c, settings(), TODAY);

        assertFalse(analysis.enoughData());
        assertFalse(types(analysis).contains("conversion"));
    }

    @Test
    void overspendingDailyBudgetTriggersPacingWithEvenDailyAmount() {
        // $600 left over 20 days (today..end) = $30/day; a $100 daily budget would run out in 6 days.
        Campaign c = campaign().dailyBudget(new BigDecimal("100")).impressions(50_000).clicks(1_300).conversions(52).build();

        OptimizationEngine.Analysis analysis = engine.analyze(c, settings(), TODAY);

        SuggestionResponse pacing = analysis.applicable(OptimizationType.BUDGET_PACING).orElseThrow();
        assertEquals("high", pacing.priority());
        assertEquals(new BigDecimal("30.00"), OptimizationEngine.recommendedDailyBudget(c, settings(), TODAY));
    }

    @Test
    void keywordsToAddSkipsExistingOnesAndRespectsTheLimit() {
        Campaign c = campaign().keywords(new ArrayList<>(List.of("deals"))).build();

        List<String> toAdd = engine.keywordsToAdd(c);

        assertEquals(OptimizationEngine.KEYWORDS_PER_SUGGESTION, toAdd.size());
        assertFalse(toAdd.contains("deals"));
    }

    @Test
    void remainingDaysHandlesFutureAndFinishedCampaigns() {
        Campaign future = campaign().startDate(TODAY.plusDays(5)).endDate(TODAY.plusDays(14)).build();
        Campaign finished = campaign().startDate(TODAY.minusDays(30)).endDate(TODAY.minusDays(1)).build();

        assertEquals(10, OptimizationEngine.remainingDays(future, TODAY));
        assertEquals(0, OptimizationEngine.remainingDays(finished, TODAY));
    }
}
