package com.adoptimizer.service;

import com.adoptimizer.dto.response.CampaignResponse;
import com.adoptimizer.dto.response.OptimizationLogResponse;
import com.adoptimizer.dto.response.OptimizationResponse;
import com.adoptimizer.exception.BadRequestException;
import com.adoptimizer.model.Campaign;
import com.adoptimizer.model.CampaignStatus;
import com.adoptimizer.model.CodedEnum;
import com.adoptimizer.model.OptimizationLog;
import com.adoptimizer.model.OptimizationType;
import com.adoptimizer.model.SystemSettings;
import com.adoptimizer.repository.CampaignRepository;
import com.adoptimizer.repository.OptimizationLogRepository;
import com.adoptimizer.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OptimizationService {

    private static final BigDecimal QUALITY_STEP = new BigDecimal("0.05");

    private final CampaignService campaignService;
    private final CampaignRepository campaignRepository;
    private final OptimizationLogRepository optimizationLogRepository;
    private final SettingsService settingsService;
    private final OptimizationEngine engine;

    /** Campaigns that can be optimized: scheduled, active or paused. */
    public List<CampaignResponse> optimizableCampaigns(long userId) {
        return campaignRepository.findByUser(userId, null).stream()
                .filter(c -> CampaignStatus.OPTIMIZABLE.contains(c.getStatus()))
                .map(CampaignResponse::from)
                .toList();
    }

    public OptimizationResponse analyze(long userId, long campaignId) {
        Campaign campaign = requireOptimizable(userId, campaignId);
        SystemSettings settings = settingsService.get();
        OptimizationEngine.Analysis analysis = engine.analyze(campaign, settings, LocalDate.now());
        return new OptimizationResponse(CampaignResponse.from(campaign), analysis.score(), analysis.scoreLabel(),
                analysis.enoughData(), campaign.getAudience().getBenchmarkCtr(),
                campaign.getAudience().getBenchmarkConversionRate(), analysis.coveragePercent(), analysis.suggestions(),
                optimizationLogRepository.findByCampaign(campaignId).stream().map(OptimizationLogResponse::from).toList());
    }

    @Transactional
    public OptimizationResponse apply(long userId, long campaignId, String typeCode) {
        Campaign campaign = requireOptimizable(userId, campaignId);
        SystemSettings settings = settingsService.get();
        LocalDate today = LocalDate.now();
        OptimizationType type = CodedEnum.requireCode(OptimizationType.class, typeCode);
        if (engine.analyze(campaign, settings, today).applicable(type).isEmpty()) {
            throw new BadRequestException("This suggestion no longer applies to the campaign. Refresh to see the latest advice.");
        }

        String description = switch (type) {
            case KEYWORDS -> {
                List<String> added = engine.keywordsToAdd(campaign);
                List<String> keywords = new ArrayList<>(campaign.getKeywords());
                keywords.addAll(added);
                campaign.setKeywords(keywords);
                raiseQuality(campaign);
                yield "Added keywords: " + String.join(", ", added);
            }
            case AD_FORMAT -> {
                String from = campaign.getAdType().getLabel();
                campaign.setAdType(campaign.getAudience().getRecommendedAdType());
                raiseQuality(campaign);
                yield "Changed ad format from " + from + " to " + campaign.getAdType().getLabel();
            }
            case SCHEDULE -> {
                campaign.setPeakHoursOnly(true);
                yield String.format(Locale.ROOT, "Enabled peak-hour scheduling (%02d:00–%02d:00)",
                        settings.getPeakHoursStart(), settings.getPeakHoursEnd());
            }
            case BUDGET_PACING -> {
                BigDecimal from = campaign.getDailyBudget();
                BigDecimal to = OptimizationEngine.recommendedDailyBudget(campaign, settings, today);
                campaign.setDailyBudget(to);
                yield "Changed daily budget from $" + from.setScale(2, RoundingMode.HALF_UP).toPlainString()
                        + " to $" + to.toPlainString();
            }
            case CONVERSION -> throw new BadRequestException("This suggestion is advice only and can't be applied automatically.");
        };

        LocalDateTime now = TimeUtils.now();
        campaign.setUpdatedAt(now);
        campaignRepository.update(campaign);
        optimizationLogRepository.insert(OptimizationLog.builder()
                .campaignId(campaignId)
                .userId(userId)
                .type(type)
                .description(description)
                .appliedAt(now)
                .build());
        return analyze(userId, campaignId);
    }

    private Campaign requireOptimizable(long userId, long campaignId) {
        Campaign campaign = campaignService.getOwned(userId, campaignId);
        if (!CampaignStatus.OPTIMIZABLE.contains(campaign.getStatus())) {
            throw new BadRequestException("Optimization is available once a campaign is approved (scheduled, active or paused).");
        }
        return campaign;
    }

    private static void raiseQuality(Campaign campaign) {
        campaign.setQualityScore(campaign.getQualityScore().add(QUALITY_STEP).min(OptimizationEngine.MAX_QUALITY));
    }
}
