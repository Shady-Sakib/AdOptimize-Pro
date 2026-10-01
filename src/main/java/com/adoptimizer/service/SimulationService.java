package com.adoptimizer.service;

import com.adoptimizer.dto.response.SimulationRunResponse;
import com.adoptimizer.model.Campaign;
import com.adoptimizer.model.CampaignStatus;
import com.adoptimizer.model.NotificationType;
import com.adoptimizer.model.SystemSettings;
import com.adoptimizer.repository.CampaignRepository;
import com.adoptimizer.repository.DailyStatRepository;
import com.adoptimizer.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Background engine that keeps campaigns moving:
 * <ol>
 *   <li>status transitions — scheduled campaigns go live on their start date; campaigns past their end date or
 *       out of budget are completed;</li>
 *   <li>traffic simulation — live campaigns receive impressions, clicks and conversions and spend budget,
 *       limited by their daily and total budgets.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {

    private static final BigDecimal ALERT_THRESHOLD = new BigDecimal("0.80");

    private final CampaignRepository campaignRepository;
    private final DailyStatRepository dailyStatRepository;
    private final SettingsService settingsService;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(initialDelayString = "${app.simulation.initial-delay-ms:15000}",
            fixedDelayString = "${app.simulation.interval-ms:20000}")
    public void scheduledTick() {
        try {
            SystemSettings settings = settingsService.get();
            int changes = applyStatusTransitions();
            int delivered = settings.isSimulationEnabled() ? deliverTraffic(settings) : 0;
            if (changes > 0 || delivered > 0) {
                log.debug("Simulation tick: {} status changes, {} campaigns delivered", changes, delivered);
            }
        } catch (RuntimeException e) {
            log.error("Simulation tick failed", e);
        }
    }

    /** Runs one tick immediately, even when the scheduled simulator is switched off (admin "Run now" button). */
    public SimulationRunResponse runOnce() {
        int changes = applyStatusTransitions();
        int delivered = deliverTraffic(settingsService.get());
        String message = delivered == 0 && changes == 0
                ? "Nothing to simulate: no campaign is live with budget left today."
                : "Delivered traffic to " + delivered + " campaign" + (delivered == 1 ? "" : "s")
                + (changes > 0 ? " and updated " + changes + " status" + (changes == 1 ? "" : "es") : "") + ".";
        return new SimulationRunResponse(delivered, changes, message);
    }

    // ------------------------------------------------------------------ status transitions

    public int applyStatusTransitions() {
        LocalDate today = LocalDate.now();
        int changes = 0;
        for (Campaign campaign : campaignRepository.findDueForStatusChange(today)) {
            Boolean changed = transactionTemplate.execute(tx -> transition(campaign, today));
            if (Boolean.TRUE.equals(changed)) {
                changes++;
            }
        }
        return changes;
    }

    private boolean transition(Campaign c, LocalDate today) {
        LocalDateTime now = TimeUtils.now();
        if (c.getEndDate().isBefore(today)) {
            if (campaignRepository.updateStatus(c.getId(), c.getStatus(), CampaignStatus.COMPLETED, now)) {
                notificationService.notify(c.getUserId(), NotificationType.INFO, "Campaign completed",
                        "\"" + c.getTitle() + "\" reached its end date. " + summary(c));
                return true;
            }
            return false;
        }
        if (c.getStatus() == CampaignStatus.ACTIVE && c.getSpent().compareTo(c.getBudget()) >= 0) {
            if (campaignRepository.updateStatus(c.getId(), CampaignStatus.ACTIVE, CampaignStatus.COMPLETED, now)) {
                notificationService.notify(c.getUserId(), NotificationType.INFO, "Budget fully spent",
                        "\"" + c.getTitle() + "\" used its entire budget and has completed. " + summary(c));
                return true;
            }
            return false;
        }
        if (c.getStatus() == CampaignStatus.SCHEDULED && !c.getStartDate().isAfter(today)) {
            if (campaignRepository.updateStatus(c.getId(), CampaignStatus.SCHEDULED, CampaignStatus.ACTIVE, now)) {
                notificationService.notify(c.getUserId(), NotificationType.SUCCESS, "Campaign is live",
                        "\"" + c.getTitle() + "\" started delivering today.");
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ traffic

    private int deliverTraffic(SystemSettings settings) {
        LocalDate today = LocalDate.now();
        int delivered = 0;
        for (Campaign campaign : campaignRepository.findByStatus(CampaignStatus.ACTIVE)) {
            if (campaign.getStartDate().isAfter(today) || campaign.getEndDate().isBefore(today)) {
                continue;
            }
            Boolean ok = transactionTemplate.execute(tx -> deliver(campaign, settings, today));
            if (Boolean.TRUE.equals(ok)) {
                delivered++;
            }
        }
        return delivered;
    }

    private boolean deliver(Campaign c, SystemSettings settings, LocalDate today) {
        BigDecimal spentToday = dailyStatRepository.spendOn(c.getId(), today);
        BigDecimal dailyLeft = c.getDailyBudget().subtract(spentToday);
        BigDecimal totalLeft = c.remainingBudget();
        BigDecimal allowance = dailyLeft.min(totalLeft);
        if (allowance.compareTo(new BigDecimal("0.01")) < 0) {
            return false;
        }

        // Each tick spends roughly 1.5–3.5% of the daily budget; peak-hour-only campaigns slow down off-peak.
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double share = 0.015 + random.nextDouble() * 0.02;
        if (c.isPeakHoursOnly() && !isPeak(settings)) {
            share *= 0.3;
        }
        BigDecimal target = c.getDailyBudget().multiply(BigDecimal.valueOf(share)).setScale(2, RoundingMode.HALF_UP);
        TrafficModel.Delivery delivery = TrafficModel.simulate(c, settings, target, allowance, random);
        if (delivery.isEmpty()) {
            return false;
        }

        LocalDateTime now = TimeUtils.now();
        campaignRepository.addDelivery(c.getId(), delivery.impressions(), delivery.clicks(), delivery.conversions(),
                delivery.spend(), now);
        dailyStatRepository.addDelivery(c.getId(), today, delivery.impressions(), delivery.clicks(),
                delivery.conversions(), delivery.spend());

        BigDecimal newSpent = c.getSpent().add(delivery.spend());
        if (newSpent.compareTo(c.getBudget()) >= 0) {
            if (campaignRepository.updateStatus(c.getId(), CampaignStatus.ACTIVE, CampaignStatus.COMPLETED, now)) {
                notificationService.notify(c.getUserId(), NotificationType.INFO, "Budget fully spent",
                        "\"" + c.getTitle() + "\" used its entire budget and has completed.");
            }
        } else if (settings.isBudgetAlerts() && !c.isBudgetAlertSent()
                && newSpent.compareTo(c.getBudget().multiply(ALERT_THRESHOLD)) >= 0) {
            campaignRepository.markBudgetAlertSent(c.getId());
            notificationService.notify(c.getUserId(), NotificationType.WARNING, "80% of budget used",
                    "\"" + c.getTitle() + "\" has spent $" + newSpent.setScale(2, RoundingMode.HALF_UP).toPlainString()
                            + " of its $" + c.getBudget().toPlainString() + " budget. Increase the budget to keep it running.");
        }
        return true;
    }

    private static boolean isPeak(SystemSettings settings) {
        int hour = LocalTime.now().getHour();
        return hour >= settings.getPeakHoursStart() && hour < settings.getPeakHoursEnd();
    }

    private static String summary(Campaign c) {
        NumberFormat number = NumberFormat.getIntegerInstance(Locale.US);
        return "Final results: " + number.format(c.getImpressions()) + " impressions, "
                + number.format(c.getClicks()) + " clicks, " + number.format(c.getConversions()) + " conversions.";
    }
}
