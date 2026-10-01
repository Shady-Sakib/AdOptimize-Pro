package com.adoptimizer.dto.response;

import java.math.BigDecimal;

/**
 * Advertiser funds.
 *
 * @param totalDeposits all completed payments
 * @param totalSpent    everything campaigns have spent so far
 * @param balance       deposits minus spend
 * @param committed     unspent budget reserved by pending, scheduled, active and paused campaigns
 * @param available     balance minus committed; what new campaigns can use
 */
public record WalletResponse(
        BigDecimal totalDeposits,
        BigDecimal totalSpent,
        BigDecimal balance,
        BigDecimal committed,
        BigDecimal available) {
}
