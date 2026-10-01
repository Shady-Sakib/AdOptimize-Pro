package com.adoptimizer.service;

import com.adoptimizer.model.AdType;
import com.adoptimizer.model.Audience;
import com.adoptimizer.model.Campaign;
import com.adoptimizer.model.SystemSettings;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficModelTest {

    private static final SystemSettings SETTINGS = SystemSettings.builder()
            .cpcRate(new BigDecimal("0.45")).cpmRate(new BigDecimal("2.50"))
            .minBudget(new BigDecimal("50")).maxDailyBudget(new BigDecimal("10000")).platformFee(new BigDecimal("5"))
            .peakHoursStart(18).peakHoursEnd(22).bannedWords("").build();

    private static Campaign campaign(AdType type, int keywords, boolean peak) {
        List<String> words = new ArrayList<>();
        for (int i = 0; i < keywords; i++) {
            words.add("keyword" + i);
        }
        return Campaign.builder().audience(Audience.MILLENNIALS).adType(type).keywords(words)
                .peakHoursOnly(peak).qualityScore(BigDecimal.ONE)
                .budget(new BigDecimal("1000")).dailyBudget(new BigDecimal("50")).spent(BigDecimal.ZERO).build();
    }

    @RepeatedTest(25)
    void neverSpendsMoreThanTheLimitAndCountsAreConsistent() {
        Random random = new Random();
        BigDecimal max = new BigDecimal(String.format(java.util.Locale.ROOT, "%.2f", 0.5 + random.nextDouble() * 20));
        TrafficModel.Delivery d = TrafficModel.simulate(campaign(AdType.VIDEO, 6, true), SETTINGS,
                new BigDecimal("25.00"), max, random);

        assertTrue(d.spend().compareTo(max) <= 0, "spent " + d.spend() + " over limit " + max);
        assertTrue(d.clicks() <= d.impressions());
        assertTrue(d.conversions() <= d.clicks());
    }

    @Test
    void tinyAllowanceDeliversNothing() {
        TrafficModel.Delivery d = TrafficModel.simulate(campaign(AdType.IMAGE, 5, false), SETTINGS,
                new BigDecimal("10"), new BigDecimal("0.00"), new Random(1));
        assertTrue(d.isEmpty());
        assertEquals(0, d.spend().signum());
    }

    @Test
    void recommendedSetupHasHigherExpectedCtr() {
        double weak = TrafficModel.expectedCtr(campaign(AdType.TEXT, 1, false));
        double optimized = TrafficModel.expectedCtr(campaign(AdType.VIDEO, 8, true));
        assertTrue(optimized > weak * 1.4, "optimized " + optimized + " vs weak " + weak);
    }

    @Test
    void keywordFactorIsCappedAtTenKeywords() {
        assertEquals(0.90, TrafficModel.keywordFactor(0), 1e-9);
        assertEquals(1.00, TrafficModel.keywordFactor(5), 1e-9);
        assertEquals(TrafficModel.keywordFactor(10), TrafficModel.keywordFactor(20), 1e-9);
    }
}
