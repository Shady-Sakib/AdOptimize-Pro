package com.adoptimizer.service;

import com.adoptimizer.dto.response.Numbers;
import com.adoptimizer.dto.response.WalletResponse;
import com.adoptimizer.repository.CampaignRepository;
import com.adoptimizer.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Advertiser funds are derived rather than stored, so they can never drift out of sync:
 * balance = payments − spend; available = balance − budget reserved by live campaigns.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final PaymentRepository paymentRepository;
    private final CampaignRepository campaignRepository;

    public WalletResponse wallet(long userId) {
        BigDecimal deposits = paymentRepository.sumCompletedByUser(userId);
        BigDecimal spent = campaignRepository.sumSpentIncludingDeleted(userId);
        BigDecimal committed = campaignRepository.sumCommittedBudget(userId);
        BigDecimal balance = deposits.subtract(spent);
        BigDecimal available = balance.subtract(committed);
        return new WalletResponse(Numbers.money(deposits), Numbers.money(spent), Numbers.money(balance),
                Numbers.money(committed), Numbers.money(available.max(BigDecimal.ZERO)));
    }

    public BigDecimal available(long userId) {
        return wallet(userId).available();
    }
}
