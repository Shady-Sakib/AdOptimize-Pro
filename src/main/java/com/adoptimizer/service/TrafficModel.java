package com.adoptimizer.service;

import com.adoptimizer.model.Campaign;
import com.adoptimizer.model.SystemSettings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.random.RandomGenerator;

/**
 * Deterministic-given-a-random-source model of how an ad campaign performs.
 * <p>
 * There is no real ad network behind this coursework project, so impressions, clicks and conversions are
 * generated from the campaign's settings. The model is intentionally tied to the optimizer's advice:
 * matching the audience's preferred ad format, having relevant keywords, peak-hour scheduling and the
 * quality score all raise the expected click-through rate, so applying suggestions visibly improves results.
 */
public final class TrafficModel {

    /** Bonus when the ad format matches what the audience responds to best. */
    public static final double FORMAT_MATCH_BONUS = 1.10;
    /** CTR lift from delivering only during peak hours. */
    public static final double PEAK_HOURS_BONUS = 1.15;

    public record Delivery(long impressions, long clicks, long conversions, BigDecimal spend) {

        public static final Delivery NONE = new Delivery(0, 0, 0, BigDecimal.ZERO.setScale(2));

        public boolean isEmpty() {
            return impressions == 0;
        }
    }

    private TrafficModel() {
    }

    /** Expected click-through rate in percent, before random noise. */
    public static double expectedCtr(Campaign c) {
        double ctr = c.getAudience().getBenchmarkCtr() * c.getAdType().getCtrMultiplier();
        if (c.getAdType() == c.getAudience().getRecommendedAdType()) {
            ctr *= FORMAT_MATCH_BONUS;
        }
        if (c.isPeakHoursOnly()) {
            ctr *= PEAK_HOURS_BONUS;
        }
        ctr *= keywordFactor(c.getKeywords().size());
        ctr *= c.getQualityScore().doubleValue();
        return ctr;
    }

    /** 0 keywords = 0.90, 5 keywords = 1.00, 10+ keywords = 1.10. */
    public static double keywordFactor(int keywordCount) {
        return 0.90 + 0.02 * Math.min(keywordCount, 10);
    }

    /** Expected conversion rate (conversions / clicks) in percent, before random noise. */
    public static double expectedConversionRate(Campaign c) {
        double rate = c.getAudience().getBenchmarkConversionRate() * c.getQualityScore().doubleValue();
        return c.isPeakHoursOnly() ? rate * 1.05 : rate;
    }

    /**
     * Generates delivery that spends roughly {@code spendTarget}, never more than {@code maxSpend}.
     */
    public static Delivery simulate(Campaign c, SystemSettings settings, BigDecimal spendTarget, BigDecimal maxSpend,
                                    RandomGenerator random) {
        BigDecimal target = spendTarget.min(maxSpend);
        if (target.compareTo(new BigDecimal("0.01")) < 0) {
            return Delivery.NONE;
        }
        double ctr = expectedCtr(c) * between(random, 0.85, 1.15) / 100.0;
        double conversionRate = expectedConversionRate(c) * between(random, 0.75, 1.25) / 100.0;
        double cpm = settings.getCpmRate().doubleValue();
        double cpc = settings.getCpcRate().doubleValue();

        double costPerImpression = cpm / 1000.0 + ctr * cpc;
        long impressions = (long) Math.floor(target.doubleValue() / costPerImpression);
        Delivery delivery = build(impressions, ctr, conversionRate, cpm, cpc);

        // Rounding clicks can push spend slightly over the limit; scale down until it fits.
        int guard = 0;
        while (delivery.spend().compareTo(maxSpend) > 0 && delivery.impressions() > 0 && guard++ < 20) {
            double ratio = maxSpend.doubleValue() / delivery.spend().doubleValue();
            impressions = Math.max(0, (long) Math.floor(delivery.impressions() * ratio) - 1);
            delivery = build(impressions, ctr, conversionRate, cpm, cpc);
        }
        return delivery.spend().compareTo(maxSpend) > 0 ? Delivery.NONE : delivery;
    }

    private static Delivery build(long impressions, double ctr, double conversionRate, double cpm, double cpc) {
        if (impressions <= 0) {
            return Delivery.NONE;
        }
        long clicks = Math.min(impressions, Math.round(impressions * ctr));
        long conversions = Math.min(clicks, Math.round(clicks * conversionRate));
        BigDecimal spend = BigDecimal.valueOf(impressions * cpm / 1000.0 + clicks * cpc).setScale(2, RoundingMode.HALF_UP);
        return new Delivery(impressions, clicks, conversions, spend);
    }

    private static double between(RandomGenerator random, double low, double high) {
        return low + random.nextDouble() * (high - low);
    }
}
