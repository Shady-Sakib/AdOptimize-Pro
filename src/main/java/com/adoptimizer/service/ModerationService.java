package com.adoptimizer.service;

import com.adoptimizer.dto.response.CampaignResponse;
import com.adoptimizer.dto.response.ModerationResponse;
import com.adoptimizer.exception.BadRequestException;
import com.adoptimizer.exception.NotFoundException;
import com.adoptimizer.model.Campaign;
import com.adoptimizer.model.SystemSettings;
import com.adoptimizer.repository.CampaignRepository;
import com.adoptimizer.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Automatic content screening against the admin-managed list of blocked words. */
@Service
@RequiredArgsConstructor
public class ModerationService {

    private final CampaignRepository campaignRepository;
    private final SettingsService settingsService;

    /** Returns the first blocked word found in the campaign's title, description or keywords. */
    public static Optional<String> findBlockedWord(Campaign campaign, List<String> blockedWords) {
        String text = String.join(" ", campaign.getTitle(), campaign.getDescription(),
                String.join(" ", campaign.getKeywords())).toLowerCase(Locale.ROOT);
        return blockedWords.stream().filter(text::contains).findFirst();
    }

    /**
     * Updates the flag fields on the (unsaved) campaign. A new match resets any earlier admin clearance,
     * because the content changed.
     */
    public void screen(Campaign campaign, SystemSettings settings) {
        if (!settings.isContentFilter()) {
            return;
        }
        Optional<String> match = findBlockedWord(campaign, settings.bannedWordList());
        if (match.isPresent()) {
            String reason = "Contains blocked phrase \"" + match.get() + "\"";
            if (!reason.equals(campaign.getFlagReason())) {
                campaign.setFlagCleared(false);
            }
            campaign.setFlagged(true);
            campaign.setFlagReason(reason);
        } else {
            campaign.setFlagged(false);
            campaign.setFlagReason(null);
            campaign.setFlagCleared(false);
        }
    }

    public ModerationResponse overview() {
        SystemSettings settings = settingsService.get();
        List<Campaign> flagged = campaignRepository.findFlaggedOpen();
        long total = campaignRepository.countNotDeleted();
        LocalDate today = LocalDate.now();
        long reviewedToday = campaignRepository.countReviewedBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        return new ModerationResponse(flagged.size(), Math.max(0, total - flagged.size()), reviewedToday,
                campaignRepository.countEverFlagged(), settings.isContentFilter(), settings.bannedWordList(),
                flagged.stream().map(CampaignResponse::from).toList());
    }

    @Transactional
    public void clearFlag(long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found."));
        if (!campaign.isFlagOpen()) {
            throw new BadRequestException("This campaign is not flagged.");
        }
        campaign.setFlagCleared(true);
        campaign.setUpdatedAt(TimeUtils.now());
        campaignRepository.update(campaign);
    }

    /** Re-checks every campaign against the current blocked word list. Returns how many are flagged afterwards. */
    @Transactional
    public int rescanAll() {
        SystemSettings settings = settingsService.get();
        if (!settings.isContentFilter()) {
            throw new BadRequestException("Turn on the content filter in Settings before scanning.");
        }
        int flagged = 0;
        for (Campaign campaign : campaignRepository.findAllNotDeleted()) {
            boolean wasFlagged = campaign.isFlagged();
            String oldReason = campaign.getFlagReason();
            boolean oldCleared = campaign.isFlagCleared();
            screen(campaign, settings);
            if (campaign.isFlagOpen()) {
                flagged++;
            }
            if (wasFlagged != campaign.isFlagged() || oldCleared != campaign.isFlagCleared()
                    || !Objects.equals(oldReason, campaign.getFlagReason())) {
                campaign.setUpdatedAt(TimeUtils.now());
                campaignRepository.update(campaign);
            }
        }
        return flagged;
    }
}
