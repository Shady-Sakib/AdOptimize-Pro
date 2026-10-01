package com.adoptimizer.service;

import com.adoptimizer.dto.request.CampaignRequest;
import com.adoptimizer.dto.response.CampaignDetailResponse;
import com.adoptimizer.dto.response.CampaignOptionsResponse;
import com.adoptimizer.dto.response.CampaignResponse;
import com.adoptimizer.dto.response.MessageResponse;
import com.adoptimizer.dto.response.OptimizationLogResponse;
import com.adoptimizer.dto.response.OptionResponse;
import com.adoptimizer.dto.response.TrendResponse;
import com.adoptimizer.exception.BadRequestException;
import com.adoptimizer.exception.FieldValidationException;
import com.adoptimizer.exception.NotFoundException;
import com.adoptimizer.model.AdType;
import com.adoptimizer.model.Audience;
import com.adoptimizer.model.Campaign;
import com.adoptimizer.model.CampaignStatus;
import com.adoptimizer.model.CodedEnum;
import com.adoptimizer.model.NotificationType;
import com.adoptimizer.model.SystemSettings;
import com.adoptimizer.repository.CampaignRepository;
import com.adoptimizer.repository.DailyStatRepository;
import com.adoptimizer.repository.OptimizationLogRepository;
import com.adoptimizer.util.TimeUtils;
import com.adoptimizer.validation.KeywordListValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Campaign lifecycle for advertisers (create, edit, pause, delete) and administrators (approve, reject, remove). */
@Service
@RequiredArgsConstructor
public class CampaignService {

    public static final int MAX_FLIGHT_DAYS = 365;
    private static final String DEFAULT_REJECTION = "Does not meet platform advertising guidelines.";

    private final CampaignRepository campaignRepository;
    private final DailyStatRepository dailyStatRepository;
    private final OptimizationLogRepository optimizationLogRepository;
    private final SettingsService settingsService;
    private final WalletService walletService;
    private final ModerationService moderationService;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;

    // ================================================================== advertiser

    public CampaignOptionsResponse options(long userId) {
        SystemSettings settings = settingsService.get();
        return new CampaignOptionsResponse(
                Arrays.stream(Audience.values()).map(a -> new OptionResponse(a.getCode(), a.getLabel())).toList(),
                Arrays.stream(AdType.values()).map(t -> new OptionResponse(t.getCode(), t.getLabel())).toList(),
                settings.getMinBudget(), settings.getMaxDailyBudget(), walletService.available(userId));
    }

    public List<CampaignResponse> listForUser(long userId, String statusCode) {
        return campaignRepository.findByUser(userId, parseStatus(statusCode)).stream().map(CampaignResponse::from).toList();
    }

    public Campaign getOwned(long userId, long campaignId) {
        return campaignRepository.findByIdAndUser(campaignId, userId)
                .orElseThrow(() -> new NotFoundException("Campaign not found."));
    }

    public CampaignDetailResponse detail(long userId, long campaignId) {
        Campaign campaign = getOwned(userId, campaignId);
        return new CampaignDetailResponse(CampaignResponse.from(campaign), last30DayTrend(campaign),
                optimizationLogRepository.findByCampaign(campaignId).stream().map(OptimizationLogResponse::from).toList());
    }

    @Transactional
    public CampaignResponse create(long userId, CampaignRequest request) {
        SystemSettings settings = settingsService.get();
        LocalDateTime now = TimeUtils.now();
        Campaign campaign = Campaign.builder()
                .userId(userId)
                .spent(BigDecimal.ZERO)
                .qualityScore(BigDecimal.ONE)
                .status(CampaignStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        applyRequest(campaign, request, settings);
        validateRules(campaign, null, settings);

        BigDecimal available = walletService.available(userId);
        if (campaign.getBudget().compareTo(available) > 0) {
            throw insufficientFunds(available);
        }

        moderationService.screen(campaign, settings);
        boolean autoApproved = settings.isAutoApprove() && !campaign.isFlagOpen();
        if (autoApproved) {
            markApproved(campaign, null, now);
        }
        campaign.setId(campaignRepository.insert(campaign));
        notifySubmitted(campaign, autoApproved);
        return CampaignResponse.from(campaign);
    }

    @Transactional
    public CampaignResponse update(long userId, long campaignId, CampaignRequest request) {
        SystemSettings settings = settingsService.get();
        Campaign existing = getOwned(userId, campaignId);
        if (existing.getStatus() == CampaignStatus.COMPLETED) {
            throw new BadRequestException("Completed campaigns can't be edited. Create a new campaign instead.");
        }
        Campaign updated = existing.toBuilder().keywords(new ArrayList<>(existing.getKeywords())).build();
        applyRequest(updated, request, settings);
        validateRules(updated, existing, settings);

        // Funds: only the extra reservation beyond what this campaign already holds must be available.
        BigDecimal alreadyReserved = CampaignStatus.BUDGET_COMMITTED.contains(existing.getStatus())
                ? existing.remainingBudget() : BigDecimal.ZERO;
        BigDecimal extraNeeded = updated.remainingBudget().subtract(alreadyReserved);
        BigDecimal available = walletService.available(userId);
        if (extraNeeded.compareTo(available) > 0) {
            throw insufficientFunds(available.add(alreadyReserved));
        }

        boolean contentChanged = !Objects.equals(existing.getTitle(), updated.getTitle())
                || !Objects.equals(existing.getDescription(), updated.getDescription())
                || existing.getAudience() != updated.getAudience()
                || existing.getAdType() != updated.getAdType()
                || !Objects.equals(existing.getKeywords(), updated.getKeywords())
                || !Objects.equals(existing.getStartDate(), updated.getStartDate());
        boolean needsReview = contentChanged || existing.getStatus() == CampaignStatus.REJECTED
                || existing.getStatus() == CampaignStatus.PENDING;

        LocalDateTime now = TimeUtils.now();
        boolean autoApproved = false;
        if (contentChanged) {
            moderationService.screen(updated, settings);
        }
        if (needsReview) {
            updated.setStatus(CampaignStatus.PENDING);
            updated.setRejectionReason(null);
            updated.setReviewedAt(null);
            updated.setReviewedBy(null);
            if (settings.isAutoApprove() && !updated.isFlagOpen()) {
                markApproved(updated, null, now);
                autoApproved = true;
            }
        }
        if (updated.getSpent().compareTo(updated.getBudget().multiply(new BigDecimal("0.8"))) < 0) {
            updated.setBudgetAlertSent(false);
        }
        updated.setUpdatedAt(now);
        campaignRepository.update(updated);

        if (needsReview && (existing.getStatus() != CampaignStatus.PENDING || autoApproved)) {
            notifySubmitted(updated, autoApproved);
        }
        return CampaignResponse.from(updated);
    }

    @Transactional
    public MessageResponse delete(long userId, long campaignId) {
        Campaign campaign = getOwned(userId, campaignId);
        campaignRepository.softDelete(campaign.getId(), TimeUtils.now());
        return new MessageResponse("Campaign \"" + campaign.getTitle() + "\" deleted.");
    }

    @Transactional
    public CampaignResponse pause(long userId, long campaignId) {
        Campaign campaign = getOwned(userId, campaignId);
        CampaignStatus current = campaign.getStatus();
        if (current != CampaignStatus.ACTIVE && current != CampaignStatus.SCHEDULED) {
            throw new BadRequestException("Only active or scheduled campaigns can be paused.");
        }
        changeStatus(campaign, CampaignStatus.PAUSED);
        return CampaignResponse.from(campaign);
    }

    @Transactional
    public CampaignResponse resume(long userId, long campaignId) {
        Campaign campaign = getOwned(userId, campaignId);
        if (campaign.getStatus() != CampaignStatus.PAUSED) {
            throw new BadRequestException("Only paused campaigns can be resumed.");
        }
        LocalDate today = LocalDate.now();
        if (campaign.getEndDate().isBefore(today)) {
            throw new BadRequestException("This campaign's end date has passed. Edit the end date to resume it.");
        }
        if (campaign.remainingBudget().compareTo(new BigDecimal("0.01")) < 0) {
            throw new BadRequestException("The budget is fully spent. Increase the budget to resume this campaign.");
        }
        changeStatus(campaign, campaign.getStartDate().isAfter(today) ? CampaignStatus.SCHEDULED : CampaignStatus.ACTIVE);
        return CampaignResponse.from(campaign);
    }

    // ================================================================== admin

    public List<CampaignResponse> listAll(String statusCode) {
        return campaignRepository.findAll(parseStatus(statusCode)).stream().map(CampaignResponse::from).toList();
    }

    @Transactional
    public CampaignResponse approve(long adminId, long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found."));
        if (campaign.getStatus() != CampaignStatus.PENDING) {
            throw new BadRequestException("Only campaigns waiting for review can be approved.");
        }
        if (campaign.getEndDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("This campaign's end date has already passed. Reject it so the advertiser can reschedule.");
        }
        LocalDateTime now = TimeUtils.now();
        markApproved(campaign, adminId, now);
        campaign.setUpdatedAt(now);
        campaignRepository.update(campaign);

        if (campaign.getStatus() == CampaignStatus.SCHEDULED) {
            notificationService.notify(campaign.getUserId(), NotificationType.SUCCESS, "Campaign approved 🎉",
                    "\"" + campaign.getTitle() + "\" was approved and will go live on " + campaign.getStartDate() + ".");
        } else {
            notificationService.notify(campaign.getUserId(), NotificationType.SUCCESS, "Campaign approved 🎉",
                    "\"" + campaign.getTitle() + "\" was approved and is now live.");
        }
        return CampaignResponse.from(campaign);
    }

    @Transactional
    public CampaignResponse reject(long adminId, long campaignId, String reason) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found."));
        if (!CampaignStatus.BUDGET_COMMITTED.contains(campaign.getStatus())) {
            throw new BadRequestException("Only pending, scheduled, active or paused campaigns can be rejected.");
        }
        String finalReason = reason == null || reason.isBlank() ? DEFAULT_REJECTION : reason.trim();
        LocalDateTime now = TimeUtils.now();
        campaign.setStatus(CampaignStatus.REJECTED);
        campaign.setRejectionReason(finalReason);
        campaign.setReviewedBy(adminId);
        campaign.setReviewedAt(now);
        campaign.setUpdatedAt(now);
        campaignRepository.update(campaign);
        notificationService.notify(campaign.getUserId(), NotificationType.ERROR, "Campaign rejected",
                "\"" + campaign.getTitle() + "\" was rejected: " + finalReason + " Edit the campaign to resubmit it.");
        return CampaignResponse.from(campaign);
    }

    @Transactional
    public MessageResponse remove(long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found."));
        campaignRepository.softDelete(campaignId, TimeUtils.now());
        notificationService.notify(campaign.getUserId(), NotificationType.WARNING, "Campaign removed",
                "\"" + campaign.getTitle() + "\" was removed by an administrator. Open a support ticket if you have questions.");
        return new MessageResponse("Campaign \"" + campaign.getTitle() + "\" removed.");
    }

    // ================================================================== shared helpers

    public static CampaignStatus parseStatus(String statusCode) {
        if (statusCode == null || statusCode.isBlank() || statusCode.equalsIgnoreCase("all")) {
            return null;
        }
        return CodedEnum.fromCode(CampaignStatus.class, statusCode)
                .orElseThrow(() -> new BadRequestException("Unknown campaign status \"" + statusCode + "\"."));
    }

    private void applyRequest(Campaign campaign, CampaignRequest request, SystemSettings settings) {
        campaign.setTitle(request.getTitle().trim().replaceAll("\\s+", " "));
        campaign.setDescription(request.getDescription().trim());
        campaign.setAudience(CodedEnum.requireCode(Audience.class, request.getAudience()));
        campaign.setAdType(CodedEnum.requireCode(AdType.class, request.getAdType()));
        campaign.setBudget(request.getBudget().setScale(2, RoundingMode.HALF_UP));
        campaign.setStartDate(request.getStartDate());
        campaign.setEndDate(request.getEndDate());
        campaign.setKeywords(new ArrayList<>(KeywordListValidator.parse(request.getKeywords())));

        if (request.getDailyBudget() != null) {
            campaign.setDailyBudget(request.getDailyBudget().setScale(2, RoundingMode.HALF_UP));
        } else {
            long days = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
            BigDecimal even = campaign.getBudget().divide(BigDecimal.valueOf(Math.max(days, 1)), 2, RoundingMode.CEILING);
            campaign.setDailyBudget(even.max(OptimizationEngine.MIN_DAILY_BUDGET)
                    .min(settings.getMaxDailyBudget()).min(campaign.getBudget()));
        }
    }

    /** Rules that depend on settings, today's date or the stored campaign. */
    private void validateRules(Campaign c, Campaign existing, SystemSettings settings) {
        LocalDate today = LocalDate.now();
        if (c.getBudget().compareTo(settings.getMinBudget()) < 0) {
            throw new FieldValidationException("budget", "Total budget must be at least $" + settings.getMinBudget().toPlainString() + ".");
        }
        if (existing != null && c.getBudget().compareTo(existing.getSpent()) < 0) {
            throw new FieldValidationException("budget", "Total budget can't be lower than the $"
                    + existing.getSpent().toPlainString() + " already spent.");
        }
        if (c.getDailyBudget().compareTo(c.getBudget()) > 0) {
            throw new FieldValidationException("dailyBudget", "Daily budget can't be more than the total budget.");
        }
        if (c.getDailyBudget().compareTo(settings.getMaxDailyBudget()) > 0) {
            throw new FieldValidationException("dailyBudget", "Daily budget can't exceed the platform limit of $"
                    + settings.getMaxDailyBudget().toPlainString() + ".");
        }
        boolean startChanged = existing == null || !existing.getStartDate().equals(c.getStartDate());
        if (startChanged && c.getStartDate().isBefore(today)) {
            throw new FieldValidationException("startDate", "Start date can't be in the past.");
        }
        if (existing != null && startChanged && existing.getSpent().signum() > 0) {
            throw new FieldValidationException("startDate", "The start date can't change after the campaign has started spending.");
        }
        if (!c.getEndDate().isAfter(c.getStartDate())) {
            throw new FieldValidationException("endDate", "End date must be after the start date.");
        }
        if (c.getEndDate().isBefore(today)) {
            throw new FieldValidationException("endDate", "End date can't be in the past.");
        }
        if (ChronoUnit.DAYS.between(c.getStartDate(), c.getEndDate()) > MAX_FLIGHT_DAYS) {
            throw new FieldValidationException("endDate", "Campaigns can run for at most " + MAX_FLIGHT_DAYS + " days.");
        }
    }

    private static void markApproved(Campaign campaign, Long adminId, LocalDateTime now) {
        campaign.setStatus(campaign.getStartDate().isAfter(LocalDate.now()) ? CampaignStatus.SCHEDULED : CampaignStatus.ACTIVE);
        campaign.setRejectionReason(null);
        campaign.setReviewedBy(adminId);
        campaign.setReviewedAt(now);
    }

    private void changeStatus(Campaign campaign, CampaignStatus next) {
        LocalDateTime now = TimeUtils.now();
        if (!campaignRepository.updateStatus(campaign.getId(), campaign.getStatus(), next, now)) {
            throw new BadRequestException("The campaign changed in the meantime. Refresh and try again.");
        }
        campaign.setStatus(next);
        campaign.setUpdatedAt(now);
    }

    private void notifySubmitted(Campaign campaign, boolean autoApproved) {
        if (autoApproved) {
            notificationService.notify(campaign.getUserId(), NotificationType.SUCCESS, "Campaign approved 🎉",
                    "\"" + campaign.getTitle() + "\" passed automatic review and is "
                            + (campaign.getStatus() == CampaignStatus.SCHEDULED ? "scheduled to start on " + campaign.getStartDate() + "." : "now live."));
        } else if (campaign.isFlagOpen()) {
            notificationService.notify(campaign.getUserId(), NotificationType.WARNING, "Campaign needs manual review",
                    "\"" + campaign.getTitle() + "\" was submitted, but our content filter flagged it ("
                            + campaign.getFlagReason().toLowerCase() + "). An administrator will review it.");
        } else {
            notificationService.notify(campaign.getUserId(), NotificationType.PENDING, "Campaign submitted",
                    "\"" + campaign.getTitle() + "\" has been submitted for admin review.");
        }
    }

    private static FieldValidationException insufficientFunds(BigDecimal available) {
        return new FieldValidationException("budget", "Not enough funds: $" + available.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + " is available. Add funds in Payments or lower the budget.");
    }

    private TrendResponse last30DayTrend(Campaign campaign) {
        LocalDate today = LocalDate.now();
        LocalDate to = campaign.getEndDate().isBefore(today) ? campaign.getEndDate() : today;
        LocalDate from = to.minusDays(29);
        if (from.isBefore(campaign.getStartDate())) {
            from = campaign.getStartDate();
        }
        if (from.isAfter(to)) {
            return new TrendResponse(List.of(), List.of(), List.of(), List.of(), List.of());
        }
        return analyticsService.buildDailyTrend(dailyStatRepository.dailyTotalsForCampaign(campaign.getId(), from, to), from, to, false);
    }
}
