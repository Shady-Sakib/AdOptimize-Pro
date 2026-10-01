package com.adoptimizer.service;

import com.adoptimizer.dto.response.AnalyticsResponse;
import com.adoptimizer.dto.response.AudienceShareResponse;
import com.adoptimizer.dto.response.ReportSummaryResponse;
import com.adoptimizer.exception.BadRequestException;
import com.adoptimizer.model.Campaign;
import com.adoptimizer.model.CampaignStatus;
import com.adoptimizer.model.Payment;
import com.adoptimizer.model.Role;
import com.adoptimizer.repository.CampaignRepository;
import com.adoptimizer.repository.OptimizationLogRepository;
import com.adoptimizer.repository.PaymentRepository;
import com.adoptimizer.repository.TicketRepository;
import com.adoptimizer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Generates downloadable CSV reports for administrators. */
@Service
@RequiredArgsConstructor
public class ReportService {

    public static final Set<String> TYPES = Set.of("performance", "revenue", "users", "campaigns", "audience", "optimization", "full");

    private final CampaignRepository campaignRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final OptimizationLogRepository optimizationLogRepository;
    private final AnalyticsService analyticsService;

    public ReportSummaryResponse summary() {
        Map<CampaignStatus, Long> counts = campaignRepository.countByStatus(null);
        return new ReportSummaryResponse(
                userRepository.countByRole(Role.ADVERTISER),
                counts.get(CampaignStatus.ACTIVE),
                counts.get(CampaignStatus.PENDING),
                counts.values().stream().mapToLong(Long::longValue).sum(),
                paymentRepository.count(),
                ticketRepository.countOpen(),
                optimizationLogRepository.count());
    }

    public String fileName(String type) {
        return type + "_report_" + LocalDate.now() + ".csv";
    }

    public String generate(String type) {
        String normalized = type == null ? "" : type.toLowerCase();
        if (!TYPES.contains(normalized)) {
            throw new BadRequestException("Unknown report type \"" + type + "\".");
        }
        List<List<Object>> rows = switch (normalized) {
            case "performance" -> performance();
            case "revenue" -> revenue();
            case "users" -> users();
            case "campaigns" -> campaigns();
            case "audience" -> audience();
            case "optimization" -> optimization();
            default -> full();
        };
        // UTF-8 byte order mark so Excel shows non-English characters correctly.
        return "\uFEFF" + toCsv(rows);
    }

    private List<List<Object>> performance() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Campaign", "Advertiser", "Status", "Audience", "Format", "Impressions", "Clicks", "CTR %",
                "Conversions", "Conversion rate %", "Spent", "Budget"));
        for (Campaign c : campaignRepository.findAll(null)) {
            rows.add(List.of(c.getTitle(), c.getOwnerName(), c.getStatus().getCode(), c.getAudience().getLabel(),
                    c.getAdType().getLabel(), c.getImpressions(), c.getClicks(), format2(c.ctr()), c.getConversions(),
                    format2(c.conversionRate()), c.getSpent(), c.getBudget()));
        }
        return rows;
    }

    private List<List<Object>> revenue() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Reference", "Advertiser", "Email", "Amount", "Method", "Description", "Date", "Status"));
        for (Payment p : paymentRepository.findAll()) {
            rows.add(List.of(String.format(Locale.ROOT, "PAY-%06d", p.getId()), p.getUserName(), p.getUserEmail(), p.getAmount(),
                    p.getMethod(), p.getDescription(), p.getCreatedAt(), p.getStatus()));
        }
        return rows;
    }

    private List<List<Object>> users() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Name", "Email", "Company", "Joined", "Last sign-in", "Campaigns", "Total spent", "Active"));
        userRepository.findAdvertiserSummaries(null, null).forEach(u -> rows.add(List.of(u.name(), u.email(),
                nullToEmpty(u.company()), u.createdAt(), u.lastLoginAt() == null ? "" : u.lastLoginAt(),
                u.campaignCount(), u.totalSpent(), u.active() ? "yes" : "no")));
        return rows;
    }

    private List<List<Object>> campaigns() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Title", "Advertiser", "Status", "Budget", "Daily budget", "Spent", "Start", "End", "Audience",
                "Keywords", "Flagged", "Rejection reason"));
        for (Campaign c : campaignRepository.findAll(null)) {
            rows.add(List.of(c.getTitle(), c.getOwnerName(), c.getStatus().getCode(), c.getBudget(), c.getDailyBudget(),
                    c.getSpent(), c.getStartDate(), c.getEndDate(), c.getAudience().getLabel(),
                    String.join("; ", c.getKeywords()), c.isFlagOpen() ? "yes" : "no", nullToEmpty(c.getRejectionReason())));
        }
        return rows;
    }

    private List<List<Object>> audience() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Audience", "Campaigns", "Impressions", "Share %"));
        AnalyticsResponse analytics = analyticsService.platformAnalytics();
        for (AudienceShareResponse share : analytics.audiences()) {
            rows.add(List.of(share.label(), share.campaigns(), share.impressions(), share.percent()));
        }
        return rows;
    }

    private List<List<Object>> optimization() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Applied at", "Campaign", "Advertiser", "Type", "Change"));
        optimizationLogRepository.findAll().forEach(log -> rows.add(List.of(log.getAppliedAt(), log.getCampaignTitle(),
                log.getUserName(), log.getType().getCode(), log.getDescription())));
        return rows;
    }

    private List<List<Object>> full() {
        List<List<Object>> rows = new ArrayList<>();
        ReportSummaryResponse s = summary();
        rows.add(List.of("AdOptimize Pro platform report", LocalDate.now()));
        rows.add(List.of());
        rows.add(List.of("Metric", "Value"));
        rows.add(List.of("Advertisers", s.totalUsers()));
        rows.add(List.of("Total campaigns", s.totalCampaigns()));
        rows.add(List.of("Active campaigns", s.activeCampaigns()));
        rows.add(List.of("Pending reviews", s.pendingReviews()));
        rows.add(List.of("Payments", s.totalPayments()));
        rows.add(List.of("Total revenue", paymentRepository.sumCompleted()));
        rows.add(List.of("Open tickets", s.openTickets()));
        rows.add(List.of("Optimizations applied", s.optimizationsApplied()));
        for (List<List<Object>> section : List.of(performance(), revenue(), users())) {
            rows.add(List.of());
            rows.addAll(section);
        }
        return rows;
    }

    // ------------------------------------------------------------------ CSV encoding

    static String toCsv(List<List<Object>> rows) {
        return rows.stream()
                .map(row -> row.stream().map(ReportService::cell).collect(Collectors.joining(",")))
                .collect(Collectors.joining("\r\n")) + "\r\n";
    }

    /** Quotes every cell and neutralises spreadsheet formula injection (cells starting with = + - @). */
    static String cell(Object value) {
        String text = value == null ? "" : value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 && !isNumber(text)) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static boolean isNumber(String text) {
        return text.matches("-?\\d+(\\.\\d+)?");
    }

    private static String format2(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
