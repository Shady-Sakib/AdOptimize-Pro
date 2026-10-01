package com.adoptimizer.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OptimizationType implements CodedEnum {
    KEYWORDS("keywords"),
    AD_FORMAT("ad-format"),
    SCHEDULE("schedule"),
    BUDGET_PACING("budget-pacing"),
    CONVERSION("conversion");

    private final String code;
}
