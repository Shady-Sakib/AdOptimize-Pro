package com.adoptimizer.service;

import com.adoptimizer.dto.response.SuggestionResponse;
import com.adoptimizer.model.AdType;
import com.adoptimizer.model.Audience;
import com.adoptimizer.model.Campaign;
import com.adoptimizer.model.OptimizationType;
import com.adoptimizer.model.SystemSettings;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rule-based campaign analysis: compares a campaign's metrics and setup with its audience benchmarks
 * and produces a 0–100 performance score plus prioritised, explainable suggestions.
 * Pure logic with no database access, so it is fully unit-testable.
 */
@Component
public class OptimizationEngine {

    /** Below this many impressions CTR is too noisy to judge. */
    public static final long MIN_IMPRESSIONS = 500;
    /** Below this many clicks conversion rate is too noisy to judge. */
    public static final long MIN_CLICKS = 30;
    public static final int MAX_KEYWORDS = 20;
    public static final int KEYWORDS_PER_SUGGESTION = 4;
    public static final BigDecimal MIN_DAILY_BUDGET = new BigDecimal("5.00");
    public static final BigDecimal MAX_QUALITY = new BigDecimal("1.50");

    private static final Map<String, Integer> PRIORITY_ORDER = Map.of("high", 0, "medium", 1, "low", 2);

    public record Analysis(int score, String scoreLabel, boolean enoughData, double coveragePercent,
                           List<SuggestionResponse> suggestions) {

        public Optional<SuggestionResponse> applicable(OptimizationType type) {
            return suggestions.stream()
                    .filter(s -> s.applicable() && s.type().equals(type.getCode()))
                    .findFirst();
        }
    }

    public Analysis analyze(Campaign c, SystemSettings settings, LocalDate today) {
        Audience audience = c.getAudience();
        boolean enoughData = c.getImpressions() >= MIN_IMPRESSIONS;
        double ctrRatio = enoughData ? c.ctr() / audience.getBenchmarkCtr() : 1.0;
        boolean enoughClicks = c.getClicks() >= MIN_CLICKS;
        double conversionRatio = enoughClicks ? c.conversionRate() / audience.getBenchmarkConversionRate() : 1.0;
        double coverage = deliveryCoverage(c, today);

        // ---- score
        double ctrPoints = enoughData ? 40.0 * Math.min(ctrRatio, 1.5) / 1.5 : 20.0;
        double conversionPoints = enoughClicks ? 30.0 * Math.min(conversionRatio, 1.5) / 1.5 : 15.0;
        double pacingPoints = 20.0 * (1.0 - Math.min(Math.abs(1.0 - coverage), 1.0));
        double setupPoints = (c.getKeywords().size() >= 5 ? 4 : 0)
                + (c.getAdType() == audience.getRecommendedAdType() ? 3 : 0)
                + (c.isPeakHoursOnly() ? 3 : 0);
        int score = (int) Math.max(0, Math.min(100, Math.round(ctrPoints + conversionPoints + pacingPoints + setupPoints)));

        // ---- suggestions
        List<SuggestionResponse> suggestions = new ArrayList<>();
        keywordSuggestion(c, enoughData, ctrRatio).ifPresent(suggestions::add);
        formatSuggestion(c, enoughData, ctrRatio).ifPresent(suggestions::add);
        scheduleSuggestion(c, settings, enoughData, ctrRatio).ifPresent(suggestions::add);
        pacingSuggestion(c, settings, today, coverage).ifPresent(suggestions::add);
        conversionSuggestion(c, enoughClicks, conversionRatio).ifPresent(suggestions::add);
        suggestions.sort(Comparator.comparingInt(s -> PRIORITY_ORDER.getOrDefault(s.priority(), 3)));

        return new Analysis(score, scoreLabel(score), enoughData, round1(coverage * 100.0), List.copyOf(suggestions));
    }

    public static String scoreLabel(int score) {
        if (score >= 80) {
            return "Excellent";
        }
        if (score >= 65) {
            return "Good";
        }
        if (score >= 45) {
            return "Needs attention";
        }
        return "Poor";
    }

    // ------------------------------------------------------------------ keyword helpers

    /** Recommended keywords for the audience that the campaign does not use yet, limited to what one suggestion adds. */
    public List<String> keywordsToAdd(Campaign c) {
        int room = Math.max(0, MAX_KEYWORDS - c.getKeywords().size());
        return c.getAudience().getRecommendedKeywords().stream()
                .filter(keyword -> !c.getKeywords().contains(keyword))
                .limit(Math.min(room, KEYWORDS_PER_SUGGESTION))
                .toList();
    }

    // ------------------------------------------------------------------ budget helpers

    /** Days the campaign can still deliver, counting today when it is inside the flight. */
    public static long remainingDays(Campaign c, LocalDate today) {
        if (today.isAfter(c.getEndDate())) {
            return 0;
        }
        LocalDate from = today.isBefore(c.getStartDate()) ? c.getStartDate() : today;
        return ChronoUnit.DAYS.between(from, c.getEndDate()) + 1;
    }

    /**
     * How much of the remaining budget the current daily budget will use by the end date (1.0 = exactly right,
     * below 1 = money left unspent, above 1 = budget runs out early).
     */
    public static double deliveryCoverage(Campaign c, LocalDate today) {
        long days = remainingDays(c, today);
        BigDecimal remaining = c.remainingBudget();
        if (days == 0 || remaining.signum() == 0) {
            return 1.0;
        }
        return c.getDailyBudget().doubleValue() * days / remaining.doubleValue();
    }

    /** Daily budget that spreads the remaining budget evenly over the remaining days, within platform limits. */
    public static BigDecimal recommendedDailyBudget(Campaign c, SystemSettings settings, LocalDate today) {
        long days = Math.max(1, remainingDays(c, today));
        BigDecimal even = c.remainingBudget().divide(BigDecimal.valueOf(days), 2, RoundingMode.CEILING);
        BigDecimal upper = settings.getMaxDailyBudget().min(c.getBudget());
        return even.max(MIN_DAILY_BUDGET).min(upper).setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ individual rules

    private Optional<SuggestionResponse> keywordSuggestion(Campaign c, boolean enoughData, double ctrRatio) {
        List<String> toAdd = keywordsToAdd(c);
        boolean fewKeywords = c.getKeywords().size() < 5;
        boolean weakCtr = enoughData && ctrRatio < 0.9;
        if (toAdd.isEmpty() || !(fewKeywords || weakCtr)) {
            return Optional.empty();
        }
        String priority = enoughData && ctrRatio < 0.75 ? "high" : "medium";
        String description = weakCtr
                ? String.format(Locale.ROOT, "CTR is %.2f%%, below the %.2f%% benchmark for %s. More relevant keywords improve targeting.",
                c.ctr(), c.getAudience().getBenchmarkCtr(), c.getAudience().getLabel())
                : String.format(Locale.ROOT, "This campaign uses %d keyword%s. Campaigns with at least 5 relevant keywords reach more of the right people.",
                c.getKeywords().size(), c.getKeywords().size() == 1 ? "" : "s");
        List<String> items = new ArrayList<>();
        toAdd.forEach(keyword -> items.add("Add \"" + keyword + "\" — popular with " + c.getAudience().getLabel()));
        return Optional.of(new SuggestionResponse(OptimizationType.KEYWORDS.getCode(), "🔑", "Expand your keywords",
                description, priority, priorityLabel(priority), items, "Add " + toAdd.size() + " suggested keywords", true));
    }

    private Optional<SuggestionResponse> formatSuggestion(Campaign c, boolean enoughData, double ctrRatio) {
        AdType recommended = c.getAudience().getRecommendedAdType();
        if (c.getAdType() == recommended) {
            return Optional.empty();
        }
        double lift = (recommended.getCtrMultiplier() * TrafficModel.FORMAT_MATCH_BONUS / c.getAdType().getCtrMultiplier() - 1.0) * 100.0;
        String priority = enoughData && ctrRatio < 0.9 ? "high" : "medium";
        List<String> items = List.of(
                c.getAudience().getLabel() + " engage most with " + recommended.getLabel().toLowerCase() + "s",
                String.format(Locale.ROOT, "Expected click-through lift of about %.0f%%", Math.max(lift, 1.0)),
                "Keeps your budget, schedule and keywords unchanged");
        return Optional.of(new SuggestionResponse(OptimizationType.AD_FORMAT.getCode(), "🎬",
                "Switch to " + recommended.getLabel(),
                "Your " + c.getAdType().getLabel().toLowerCase() + " is not the best-performing format for this audience.",
                priority, priorityLabel(priority), items, "Switch to " + recommended.getLabel(), true));
    }

    private Optional<SuggestionResponse> scheduleSuggestion(Campaign c, SystemSettings settings, boolean enoughData, double ctrRatio) {
        if (c.isPeakHoursOnly()) {
            return Optional.empty();
        }
        String window = String.format(Locale.ROOT, "%02d:00–%02d:00", settings.getPeakHoursStart(), settings.getPeakHoursEnd());
        String priority = enoughData && ctrRatio < 1.0 ? "medium" : "low";
        List<String> items = List.of(
                "Focus delivery on the platform peak window, " + window,
                String.format(Locale.ROOT, "Typically lifts CTR by around %.0f%%", (TrafficModel.PEAK_HOURS_BONUS - 1) * 100),
                "Fewer impressions wasted while your audience is inactive");
        return Optional.of(new SuggestionResponse(OptimizationType.SCHEDULE.getCode(), "🕒",
                "Schedule ads for peak hours",
                "Ads currently run all day, including hours with low engagement.",
                priority, priorityLabel(priority), items, "Enable peak-hour scheduling", true));
    }

    private Optional<SuggestionResponse> pacingSuggestion(Campaign c, SystemSettings settings, LocalDate today, double coverage) {
        long days = remainingDays(c, today);
        if (days == 0 || c.remainingBudget().compareTo(MIN_DAILY_BUDGET) < 0) {
            return Optional.empty();
        }
        BigDecimal recommended = recommendedDailyBudget(c, settings, today);
        BigDecimal difference = recommended.subtract(c.getDailyBudget()).abs();
        BigDecimal threshold = c.getDailyBudget().multiply(new BigDecimal("0.05")).max(BigDecimal.ONE);
        boolean underSpending = coverage < 0.8;
        boolean overSpending = coverage > 1.5;
        if (!(underSpending || overSpending) || difference.compareTo(threshold) < 0) {
            return Optional.empty();
        }
        String title;
        String description;
        String priority;
        if (overSpending) {
            long daysOfBudget = c.getDailyBudget().signum() == 0 ? days
                    : c.remainingBudget().divide(c.getDailyBudget(), 0, RoundingMode.DOWN).longValue();
            title = "Pace your budget to the end date";
            description = String.format(Locale.ROOT, "At the current daily budget the money runs out in about %d day%s, %d day%s before the campaign ends.",
                    daysOfBudget, daysOfBudget == 1 ? "" : "s", days - daysOfBudget, days - daysOfBudget == 1 ? "" : "s");
            priority = "high";
        } else {
            BigDecimal unspent = c.remainingBudget().subtract(c.getDailyBudget().multiply(BigDecimal.valueOf(days))).max(BigDecimal.ZERO);
            title = "Use your full budget";
            description = String.format(Locale.ROOT, "At the current daily budget about $%s of your budget would stay unspent.",
                    unspent.setScale(2, RoundingMode.HALF_UP).toPlainString());
            priority = "medium";
        }
        List<String> items = List.of(
                "Current daily budget: $" + c.getDailyBudget().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "Recommended daily budget: $" + recommended.toPlainString(),
                String.format(Locale.ROOT, "Remaining budget $%s over %d day%s", c.remainingBudget().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        days, days == 1 ? "" : "s"));
        return Optional.of(new SuggestionResponse(OptimizationType.BUDGET_PACING.getCode(), "💰", title, description,
                priority, priorityLabel(priority), items, "Set daily budget to $" + recommended.toPlainString(), true));
    }

    private Optional<SuggestionResponse> conversionSuggestion(Campaign c, boolean enoughClicks, double conversionRatio) {
        if (!enoughClicks || conversionRatio >= 0.8) {
            return Optional.empty();
        }
        String priority = conversionRatio < 0.5 ? "high" : "medium";
        List<String> items = List.of(
                "Make the landing page headline repeat the promise made in the ad",
                "Place one clear call to action above the fold",
                "Remove optional form fields and add reviews near the call to action",
                "Check that the landing page loads in under 3 seconds on mobile");
        return Optional.of(new SuggestionResponse(OptimizationType.CONVERSION.getCode(), "🎯",
                "Improve post-click conversion",
                String.format(Locale.ROOT, "Conversion rate is %.2f%% against a %.2f%% benchmark. People click but don't complete the goal.",
                        c.conversionRate(), c.getAudience().getBenchmarkConversionRate()),
                priority, priorityLabel(priority), items, null, false));
    }

    private static String priorityLabel(String priority) {
        return switch (priority) {
            case "high" -> "High impact";
            case "medium" -> "Medium impact";
            default -> "Quick win";
        };
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
