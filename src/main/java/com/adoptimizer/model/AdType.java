package com.adoptimizer.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdType implements CodedEnum {
    IMAGE("image", "Image Ad", 1.00),
    VIDEO("video", "Video Ad", 1.15),
    TEXT("text", "Text Ad", 0.85),
    CAROUSEL("carousel", "Carousel Ad", 1.08);

    private final String code;
    private final String label;
    /** Relative click-through-rate multiplier used by the traffic simulator. */
    private final double ctrMultiplier;
}
