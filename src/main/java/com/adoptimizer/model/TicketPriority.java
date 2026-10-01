package com.adoptimizer.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TicketPriority implements CodedEnum {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String code;
}
