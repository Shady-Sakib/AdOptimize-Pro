package com.adoptimizer.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType implements CodedEnum {
    SUCCESS("success"),
    WARNING("warning"),
    INFO("info"),
    ERROR("error"),
    PENDING("pending");

    private final String code;
}
