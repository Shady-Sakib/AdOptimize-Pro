package com.adoptimizer.service;

import com.adoptimizer.dto.response.AdminDashboardResponse;
import com.adoptimizer.dto.response.AnalyticsResponse;
import com.adoptimizer.dto.response.AudienceShareResponse;
import com.adoptimizer.dto.response.BudgetResponse;
import com.adoptimizer.dto.response.CampaignResponse;
import com.adoptimizer.dto.response.KpiResponse;
import com.adoptimizer.dto.response.MoneyTrendResponse;
import com.adoptimizer.dto.response.Numbers;
import com.adoptimizer.dto.response.OverviewResponse;
import com.adoptimizer.dto.response.PaymentResponse;
import com.adoptimizer.dto.response.RevenueResponse;
import com.adoptimizer.dto.response.TicketResponse;
import com.adoptimizer.dto.response.TrendResponse;
import com.adoptimizer.exception.BadRequestException;
import com.adoptimizer.model.Audience;
import com.adoptimizer.model.Campaign;
import com.adoptimizer.model.CampaignStatus;
import com.adoptimizer.model.DailyStat;
import com.adoptimizer.model.Payment;
import com.adoptimizer.model.Role;
import com.adoptimizer.model.SystemSettings;
import com.adoptimizer.model.TicketStatus;
import com.adoptimizer.repository.CampaignRepository;
import com.adoptimizer.repository.DailyStatRepository;
import com.adoptimizer.repository.PaymentRepository;
import com.adoptimizer.repository.SettingsRepository;
import com.adoptimizer.repository.TicketRepository;
import com.adoptimizer.repository.UserRepository;
import com.adoptimizer.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only reporting: advertiser overview and budget, admin dashboard, analytics and revenue. */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final Set<Integer> ALLOWED_PERIODS = Set.of(7, 30, 90);

    private final CampaignRepository campaignRepository;
    private final DailyStatRepository dailyStatRepository;
    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final SettingsRepository settingsRepository;
    private final WalletService walletService;

    // ================================================================== advertiser

    public OverviewResponse overview(long userId, int days) {
        if (!ALLOWED_PERIODS.contains(days)) {
            throw new BadRequestException("Choose a period of 7, 30 or 90 days.");
        }
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1L);
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1L);

        List<DailyStat> rows = dailyStatRepository.dailyTotalsForUser(userId, previousFrom, to);
        List<DailyStat> current = rows.stream().filter(r -> !r.getStatDate().isBefore(from)).toList();
        List<DailyStat> previous = rows.stream().filter(r -> r.getStatDate().isBefore(from)).toList();

        DailyStat now = sum(current);
        DailyStat before = sum(previous);
        double ctr = rate(now.getClicks(), now.getImpressions());
        double previousCtr = rate(before.getClicks(), before.getImpressions());
        KpiResponse kpis = new KpiResponse(now.getImpressions(), now.getClicks(), now.getConversions(),
                Numbers.money(now.getSpend()), Numbers.round2(ctr),
                Numbers.percentChange(now.getImpressions(), before.getImpressions()),
                Numbers.percentChange(now.getClicks(), before.getClicks()),
                previous.isEmpty() ? null : Numbers.percentChange(ctr, previousCtr),
                Numbers.percentChange(now.getSpend().doubleValue(), before.getSpend().doubleValue()));

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        campaignRepository.countByStatus(userId).forEach((status, count) -> statusCounts.put(status.getCode(), count));

        List<CampaignResponse> recent = campaignRepository.findByUser(userId, null).stream()
                .limit(5).map(CampaignResponse::from).toList();

        return new OverviewResponse(days, kpis, buildDailyTrend(current, from, to, days > 30), statusCounts, recent,
                walletService.wallet(userId));
    }

    public BudgetResponse budget(long userId) {
        List<Campaign> campaigns = campaignRepository.findByUser(userId, null);
        List<Campaign> counted = campaigns.stream().filter(c -> c.getStatus() != CampaignStatus.REJECTED).toList();
        BigDecimal budgeted = counted.stream().map(Campaign::getBudget).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spent = counted.stream().map(Campaign::getSpent).reduce(BigDecimal.ZERO, BigDecimal::add);
        int usedPercent = budgeted.signum() == 0 ? 0
                : spent.multiply(BigDecimal.valueOf(100)).divide(budgeted, 0, RoundingMode.HALF_UP).intValue();
        return new BudgetResponse(walletService.wallet(userId), Numbers.money(budgeted), Numbers.money(spent),
                Numbers.money(budgeted.subtract(spent).max(BigDecimal.ZERO)), Math.min(usedPercent, 100),
                campaigns.stream().map(CampaignResponse::from).toList(), monthlyTrend(userId, 6));
    }

    // ================================================================== admin

    public AdminDashboardResponse adminDashboard() {
        Map<CampaignStatus, Long> counts = campaignRepository.countByStatus(null);
        long totalCampaigns = counts.values().stream().mapToLong(Long::longValue).sum();
        List<TicketResponse> openTickets = ticketRepository.findAll(TicketStatus.OPEN).stream()
                .limit(3).map(TicketResponse::from).toList();
        return new AdminDashboardResponse(
                userRepository.countByRole(Role.ADVERTISER),
                userRepository.countActiveByRole(Role.ADVERTISER),
                totalCampaigns,
                counts.get(CampaignStatus.ACTIVE),
                counts.get(CampaignStatus.PENDING),
                ticketRepository.countOpen(),
                Numbers.money(paymentRepository.sumCompleted()),
                monthlyTrend(null, 6),
                revenueVsSpend(6),
                campaignRepository.findPendingOldestFirst(3).stream().map(CampaignResponse::from).toList(),
                openTickets);
    }

    public AnalyticsResponse platformAnalytics() {
        long[] totals = campaignRepository.platformTotals();
        Map<Audience, long[]> byAudience = campaignRepository.totalsByAudience();
        long totalImpressions = byAudience.values().stream().mapToLong(v -> v[0]).sum();
        long totalCampaigns = byAudience.values().stream().mapToLong(v -> v[1]).sum();
        List<AudienceShareResponse> audiences = new ArrayList<>();
        byAudience.forEach((audience, values) -> {
            double percent = totalImpressions > 0 ? values[0] * 100.0 / totalImpressions
                    : totalCampaigns > 0 ? values[1] * 100.0 / totalCampaigns : 0.0;
            audiences.add(new AudienceShareResponse(audience.getCode(), audience.getLabel(), values[0], values[1],
                    Numbers.round2(percent)));
        });
        return new AnalyticsResponse(totals[0], totals[1], totals[2], Numbers.round2(rate(totals[1], totals[0])),
                monthlyTrend(null, 7), audiences, revenueVsSpend(6));
    }

    public RevenueResponse revenue() {
        SystemSettings settings = settingsRepository.get();
        List<Payment> payments = paymentRepository.findAll();
        BigDecimal total = paymentRepository.sumCompleted();
        BigDecimal earnings = total.multiply(settings.getPlatformFee()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new RevenueResponse(Numbers.money(total), earnings, Numbers.money(settings.getPlatformFee()), payments.size(),
                revenueVsSpend(6), payments.stream().map(PaymentResponse::from).toList());
    }

    // ================================================================== trend builders

    /**
     * Fills every day between {@code from} and {@code to} (missing days = 0). When {@code weekly} is true,
     * days are grouped into 7-day buckets labelled with the bucket's first day.
     */
    public TrendResponse buildDailyTrend(List<DailyStat> rows, LocalDate from, LocalDate to, boolean weekly) {
        Map<LocalDate, DailyStat> byDate = new HashMap<>();
        rows.forEach(row -> byDate.put(row.getStatDate(), row));
        int bucketSize = weekly ? 7 : 1;

        List<String> labels = new ArrayList<>();
        List<Long> impressions = new ArrayList<>();
        List<Long> clicks = new ArrayList<>();
        List<Long> conversions = new ArrayList<>();
        List<BigDecimal> spend = new ArrayList<>();
        for (LocalDate bucketStart = from; !bucketStart.isAfter(to); bucketStart = bucketStart.plusDays(bucketSize)) {
            long imp = 0;
            long clk = 0;
            long conv = 0;
            BigDecimal money = BigDecimal.ZERO;
            for (int i = 0; i < bucketSize; i++) {
                LocalDate day = bucketStart.plusDays(i);
                if (day.isAfter(to)) {
                    break;
                }
                DailyStat stat = byDate.get(day);
                if (stat != null) {
                    imp += stat.getImpressions();
                    clk += stat.getClicks();
                    conv += stat.getConversions();
                    money = money.add(stat.getSpend());
                }
            }
            labels.add(bucketStart.format(TimeUtils.DAY_LABEL));
            impressions.add(imp);
            clicks.add(clk);
            conversions.add(conv);
            spend.add(Numbers.money(money));
        }
        return new TrendResponse(labels, impressions, clicks, conversions, spend);
    }

    /** Monthly delivery totals for the last {@code months} months; {@code userId == null} = whole platform. */
    public TrendResponse monthlyTrend(Long userId, int months) {
        List<YearMonth> range = TimeUtils.lastMonths(months);
        Map<YearMonth, DailyStat> data = dailyStatRepository.monthlyTotals(userId, range.get(0).atDay(1));
        List<String> labels = new ArrayList<>();
        List<Long> impressions = new ArrayList<>();
        List<Long> clicks = new ArrayList<>();
        List<Long> conversions = new ArrayList<>();
        List<BigDecimal> spend = new ArrayList<>();
        for (YearMonth month : range) {
            DailyStat stat = data.get(month);
            labels.add(month.format(TimeUtils.MONTH_LABEL));
            impressions.add(stat == null ? 0L : stat.getImpressions());
            clicks.add(stat == null ? 0L : stat.getClicks());
            conversions.add(stat == null ? 0L : stat.getConversions());
            spend.add(Numbers.money(stat == null ? BigDecimal.ZERO : stat.getSpend()));
        }
        return new TrendResponse(labels, impressions, clicks, conversions, spend);
    }

    /** Monthly payments received versus money spent by campaigns. */
    public MoneyTrendResponse revenueVsSpend(int months) {
        List<YearMonth> range = TimeUtils.lastMonths(months);
        Map<YearMonth, BigDecimal> payments = paymentRepository.monthlyTotals(range.get(0).atDay(1).atStartOfDay());
        TrendResponse delivery = monthlyTrend(null, months);
        List<String> labels = new ArrayList<>();
        List<BigDecimal> revenue = new ArrayList<>();
        for (YearMonth month : range) {
            labels.add(month.format(TimeUtils.MONTH_LABEL));
            revenue.add(Numbers.money(payments.getOrDefault(month, BigDecimal.ZERO)));
        }
        return new MoneyTrendResponse(labels, revenue, delivery.spend());
    }

    // ================================================================== helpers

    private static DailyStat sum(List<DailyStat> rows) {
        DailyStat total = DailyStat.builder().spend(BigDecimal.ZERO).build();
        for (DailyStat row : rows) {
            total.setImpressions(total.getImpressions() + row.getImpressions());
            total.setClicks(total.getClicks() + row.getClicks());
            total.setConversions(total.getConversions() + row.getConversions());
            total.setSpend(total.getSpend().add(row.getSpend()));
        }
        return total;
    }

    private static double rate(long part, long whole) {
        return whole == 0 ? 0.0 : part * 100.0 / whole;
    }
}
