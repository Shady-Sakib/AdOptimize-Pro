package com.adoptimizer.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TicketStatus implements CodedEnum {
    OPEN("open"),
    RESOLVED("resolved");

    private final String code;
}
