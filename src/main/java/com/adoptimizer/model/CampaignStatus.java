package com.adoptimizer.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum CampaignStatus implements CodedEnum {
    /** Submitted, waiting for an administrator. */
    PENDING("pending"),
    /** Approved, but the start date is still in the future. */
    SCHEDULED("scheduled"),
    /** Approved and delivering impressions. */
    ACTIVE("active"),
    /** Stopped temporarily by the advertiser. */
    PAUSED("paused"),
    /** End date passed or budget exhausted. */
    COMPLETED("completed"),
    /** Declined by an administrator. */
    REJECTED("rejected");

    private final String code;

    /** Statuses whose unspent budget is reserved from the advertiser's wallet. */
    public static final Set<CampaignStatus> BUDGET_COMMITTED = EnumSet.of(PENDING, SCHEDULED, ACTIVE, PAUSED);

    /** Statuses that can receive optimization suggestions. */
    public static final Set<CampaignStatus> OPTIMIZABLE = EnumSet.of(SCHEDULED, ACTIVE, PAUSED);
}
