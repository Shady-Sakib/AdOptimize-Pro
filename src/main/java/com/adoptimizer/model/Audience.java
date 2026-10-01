package com.adoptimizer.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Target audience segments together with the industry benchmarks the optimizer compares against.
 */
@Getter
@RequiredArgsConstructor
public enum Audience implements CodedEnum {
    MILLENNIALS("millennials", "Millennials (18–34)", 2.10, 3.20, AdType.VIDEO,
            List.of("lifestyle", "deals", "travel", "online shopping", "subscription", "eco-friendly")),
    GEN_Z("gen-z", "Gen Z (13–24)", 1.90, 2.40, AdType.VIDEO,
            List.of("trending", "gaming", "streetwear", "viral", "student deals", "music")),
    TECH_ENTHUSIASTS("tech-enthusiasts", "Tech Enthusiasts", 2.60, 3.80, AdType.CAROUSEL,
            List.of("gadgets", "innovation", "smart home", "tech review", "early access", "ai")),
    FAMILIES("families", "Families", 1.70, 3.00, AdType.IMAGE,
            List.of("family", "kids", "home essentials", "value pack", "safety", "back to school")),
    PROFESSIONALS("professionals", "Professionals", 2.30, 4.40, AdType.TEXT,
            List.of("productivity", "career", "b2b", "software", "business tools", "remote work")),
    SENIORS("seniors", "Seniors (50+)", 1.50, 2.90, AdType.IMAGE,
            List.of("health", "retirement", "comfort", "trusted", "easy to use", "savings"));

    private final String code;
    private final String label;
    /** Benchmark click-through rate, in percent. */
    private final double benchmarkCtr;
    /** Benchmark conversion rate (conversions / clicks), in percent. */
    private final double benchmarkConversionRate;
    private final AdType recommendedAdType;
    private final List<String> recommendedKeywords;
}
