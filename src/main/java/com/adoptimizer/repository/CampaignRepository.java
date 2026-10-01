package com.adoptimizer.repository;

import com.adoptimizer.model.AdType;
import com.adoptimizer.model.Audience;
import com.adoptimizer.model.Campaign;
import com.adoptimizer.model.CampaignStatus;
import com.adoptimizer.model.CodedEnum;
import com.adoptimizer.util.SqlUtils;
import com.adoptimizer.validation.KeywordListValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CampaignRepository {

    private static final String SELECT = """
            SELECT c.*, u.name AS owner_name, u.email AS owner_email
            FROM campaigns c
            JOIN users u ON u.id = c.user_id
            """;

    private static final String COMMITTED_STATUSES = CampaignStatus.BUDGET_COMMITTED.stream()
            .map(status -> "'" + status.name() + "'")
            .collect(Collectors.joining(", "));

    private static final RowMapper<Campaign> MAPPER = (rs, rowNum) -> Campaign.builder()
            .id(rs.getLong("id"))
            .userId(rs.getLong("user_id"))
            .title(rs.getString("title"))
            .description(rs.getString("description"))
            .audience(CodedEnum.requireCode(Audience.class, rs.getString("audience")))
            .adType(CodedEnum.requireCode(AdType.class, rs.getString("ad_type")))
            .budget(rs.getBigDecimal("budget"))
            .dailyBudget(rs.getBigDecimal("daily_budget"))
            .spent(rs.getBigDecimal("spent"))
            .startDate(SqlUtils.date(rs, "start_date"))
            .endDate(SqlUtils.date(rs, "end_date"))
            .keywords(new ArrayList<>(KeywordListValidator.parse(rs.getString("keywords"))))
            .status(CampaignStatus.valueOf(rs.getString("status")))
            .rejectionReason(rs.getString("rejection_reason"))
            .flagged(rs.getBoolean("flagged"))
            .flagReason(rs.getString("flag_reason"))
            .flagCleared(rs.getBoolean("flag_cleared"))
            .peakHoursOnly(rs.getBoolean("peak_hours_only"))
            .qualityScore(rs.getBigDecimal("quality_score"))
            .impressions(rs.getLong("impressions"))
            .clicks(rs.getLong("clicks"))
            .conversions(rs.getLong("conversions"))
            .budgetAlertSent(rs.getBoolean("budget_alert_sent"))
            .reviewedBy(SqlUtils.nullableLong(rs, "reviewed_by"))
            .reviewedAt(SqlUtils.dateTime(rs, "reviewed_at"))
            .createdAt(SqlUtils.dateTime(rs, "created_at"))
            .updatedAt(SqlUtils.dateTime(rs, "updated_at"))
            .deletedAt(SqlUtils.dateTime(rs, "deleted_at"))
            .ownerName(rs.getString("owner_name"))
            .ownerEmail(rs.getString("owner_email"))
            .build();

    private final JdbcClient jdbc;

    // ------------------------------------------------------------------ writes

    public long insert(Campaign c) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO campaigns (user_id, title, description, audience, ad_type, budget, daily_budget, spent,
                    start_date, end_date, keywords, status, rejection_reason, flagged, flag_reason, flag_cleared,
                    peak_hours_only, quality_score, impressions, clicks, conversions, budget_alert_sent,
                    reviewed_by, reviewed_at, created_at, updated_at)
                VALUES (:userId, :title, :description, :audience, :adType, :budget, :dailyBudget, :spent,
                    :startDate, :endDate, :keywords, :status, :rejectionReason, :flagged, :flagReason, :flagCleared,
                    :peakHoursOnly, :qualityScore, :impressions, :clicks, :conversions, :budgetAlertSent,
                    :reviewedBy, :reviewedAt, :createdAt, :updatedAt)
                """)
                .params(toParams(c))
                .update(keys, "id");
        return Objects.requireNonNull(keys.getKey(), "No generated key for campaign").longValue();
    }

    /** Persists every mutable column of the campaign. */
    public void update(Campaign c) {
        Map<String, Object> params = toParams(c);
        params.put("id", c.getId());
        jdbc.sql("""
                UPDATE campaigns SET title = :title, description = :description, audience = :audience,
                    ad_type = :adType, budget = :budget, daily_budget = :dailyBudget, start_date = :startDate,
                    end_date = :endDate, keywords = :keywords, status = :status, rejection_reason = :rejectionReason,
                    flagged = :flagged, flag_reason = :flagReason, flag_cleared = :flagCleared,
                    peak_hours_only = :peakHoursOnly, quality_score = :qualityScore,
                    budget_alert_sent = :budgetAlertSent, reviewed_by = :reviewedBy, reviewed_at = :reviewedAt,
                    updated_at = :updatedAt
                WHERE id = :id AND deleted_at IS NULL
                """)
                .params(params)
                .update();
    }

    /** Status change guarded by the expected current status, so concurrent changes cannot overwrite each other. */
    public boolean updateStatus(long id, CampaignStatus expected, CampaignStatus next, LocalDateTime now) {
        return jdbc.sql("""
                UPDATE campaigns SET status = :next, updated_at = :now
                WHERE id = :id AND status = :expected AND deleted_at IS NULL
                """)
                .param("next", next.name())
                .param("now", now)
                .param("id", id)
                .param("expected", expected.name())
                .update() == 1;
    }

    /** Adds simulated delivery to the running totals. */
    public void addDelivery(long id, long impressions, long clicks, long conversions, BigDecimal spend, LocalDateTime now) {
        jdbc.sql("""
                UPDATE campaigns
                SET impressions = impressions + :impressions, clicks = clicks + :clicks,
                    conversions = conversions + :conversions, spent = spent + :spend, updated_at = :now
                WHERE id = :id
                """)
                .param("impressions", impressions)
                .param("clicks", clicks)
                .param("conversions", conversions)
                .param("spend", spend)
                .param("now", now)
                .param("id", id)
                .update();
    }

    public void markBudgetAlertSent(long id) {
        jdbc.sql("UPDATE campaigns SET budget_alert_sent = TRUE WHERE id = :id").param("id", id).update();
    }

    public void softDelete(long id, LocalDateTime now) {
        jdbc.sql("UPDATE campaigns SET deleted_at = :now, updated_at = :now WHERE id = :id AND deleted_at IS NULL")
                .param("now", now)
                .param("id", id)
                .update();
    }

    // ------------------------------------------------------------------ single reads

    public Optional<Campaign> findById(long id) {
        return jdbc.sql(SELECT + " WHERE c.id = :id AND c.deleted_at IS NULL")
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    public Optional<Campaign> findByIdAndUser(long id, long userId) {
        return jdbc.sql(SELECT + " WHERE c.id = :id AND c.user_id = :userId AND c.deleted_at IS NULL")
                .param("id", id)
                .param("userId", userId)
                .query(MAPPER)
                .optional();
    }

    // ------------------------------------------------------------------ list reads

    public List<Campaign> findByUser(long userId, CampaignStatus status) {
        String sql = SELECT + " WHERE c.user_id = :userId AND c.deleted_at IS NULL"
                + (status != null ? " AND c.status = :status" : "")
                + " ORDER BY c.created_at DESC, c.id DESC";
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("userId", userId);
        if (status != null) {
            spec = spec.param("status", status.name());
        }
        return spec.query(MAPPER).list();
    }

    public List<Campaign> findAll(CampaignStatus status) {
        String sql = SELECT + " WHERE c.deleted_at IS NULL"
                + (status != null ? " AND c.status = :status" : "")
                + " ORDER BY c.created_at DESC, c.id DESC";
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        if (status != null) {
            spec = spec.param("status", status.name());
        }
        return spec.query(MAPPER).list();
    }

    public List<Campaign> findPendingOldestFirst(int limit) {
        return jdbc.sql(SELECT + " WHERE c.deleted_at IS NULL AND c.status = 'PENDING' ORDER BY c.created_at ASC, c.id ASC LIMIT :limit")
                .param("limit", limit)
                .query(MAPPER)
                .list();
    }

    public List<Campaign> findFlaggedOpen() {
        return jdbc.sql(SELECT + """
                 WHERE c.deleted_at IS NULL AND c.flagged = TRUE AND c.flag_cleared = FALSE
                 ORDER BY c.created_at DESC
                """)
                .query(MAPPER)
                .list();
    }

    /** Every non-deleted campaign; used by the moderation re-scan. */
    public List<Campaign> findAllNotDeleted() {
        return jdbc.sql(SELECT + " WHERE c.deleted_at IS NULL ORDER BY c.id").query(MAPPER).list();
    }

    public List<Campaign> findByStatus(CampaignStatus status) {
        return jdbc.sql(SELECT + " WHERE c.deleted_at IS NULL AND c.status = :status ORDER BY c.id")
                .param("status", status.name())
                .query(MAPPER)
                .list();
    }

    // ------------------------------------------------------------------ aggregates

    public BigDecimal sumCommittedBudget(long userId) {
        return jdbc.sql("SELECT COALESCE(SUM(budget - spent), 0) FROM campaigns WHERE user_id = :userId"
                        + " AND deleted_at IS NULL AND status IN (" + COMMITTED_STATUSES + ")")
                .param("userId", userId)
                .query(BigDecimal.class)
                .single();
    }

    /** Total spend including removed campaigns (money already spent stays spent). */
    public BigDecimal sumSpentIncludingDeleted(long userId) {
        return jdbc.sql("SELECT COALESCE(SUM(spent), 0) FROM campaigns WHERE user_id = :userId")
                .param("userId", userId)
                .query(BigDecimal.class)
                .single();
    }

    public BigDecimal sumSpentAll() {
        return jdbc.sql("SELECT COALESCE(SUM(spent), 0) FROM campaigns").query(BigDecimal.class).single();
    }

    public Map<CampaignStatus, Long> countByStatus(Long userId) {
        String sql = "SELECT status, COUNT(*) AS total FROM campaigns WHERE deleted_at IS NULL"
                + (userId != null ? " AND user_id = :userId" : "")
                + " GROUP BY status";
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        if (userId != null) {
            spec = spec.param("userId", userId);
        }
        Map<CampaignStatus, Long> counts = new EnumMap<>(CampaignStatus.class);
        for (CampaignStatus status : CampaignStatus.values()) {
            counts.put(status, 0L);
        }
        spec.query((rs, rowNum) -> Map.entry(CampaignStatus.valueOf(rs.getString("status")), rs.getLong("total")))
                .list()
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    /** Lifetime totals per audience: impressions and campaign count. */
    public Map<Audience, long[]> totalsByAudience() {
        Map<Audience, long[]> totals = new LinkedHashMap<>();
        for (Audience audience : Audience.values()) {
            totals.put(audience, new long[]{0L, 0L});
        }
        jdbc.sql("""
                SELECT audience, COALESCE(SUM(impressions), 0) AS impressions, COUNT(*) AS campaigns
                FROM campaigns WHERE deleted_at IS NULL GROUP BY audience
                """)
                .query((rs, rowNum) -> {
                    Audience audience = CodedEnum.requireCode(Audience.class, rs.getString("audience"));
                    totals.put(audience, new long[]{rs.getLong("impressions"), rs.getLong("campaigns")});
                    return audience;
                })
                .list();
        return totals;
    }

    public long[] platformTotals() {
        return jdbc.sql("""
                SELECT COALESCE(SUM(impressions), 0) AS impressions, COALESCE(SUM(clicks), 0) AS clicks,
                       COALESCE(SUM(conversions), 0) AS conversions
                FROM campaigns WHERE deleted_at IS NULL
                """)
                .query((rs, rowNum) -> new long[]{rs.getLong("impressions"), rs.getLong("clicks"), rs.getLong("conversions")})
                .single();
    }

    public long countReviewedBetween(LocalDateTime from, LocalDateTime to) {
        return jdbc.sql("SELECT COUNT(*) FROM campaigns WHERE reviewed_at >= :from AND reviewed_at < :to")
                .param("from", from)
                .param("to", to)
                .query(Long.class)
                .single();
    }

    public long countEverFlagged() {
        return jdbc.sql("SELECT COUNT(*) FROM campaigns WHERE flagged = TRUE").query(Long.class).single();
    }

    public long countNotDeleted() {
        return jdbc.sql("SELECT COUNT(*) FROM campaigns WHERE deleted_at IS NULL").query(Long.class).single();
    }

    /** Campaigns due for an automatic status change on the given day. */
    public List<Campaign> findDueForStatusChange(LocalDate today) {
        return jdbc.sql(SELECT + """
                 WHERE c.deleted_at IS NULL AND (
                       (c.status = 'SCHEDULED' AND c.start_date <= :today)
                    OR (c.status IN ('SCHEDULED', 'ACTIVE', 'PAUSED') AND c.end_date < :today)
                    OR (c.status = 'ACTIVE' AND c.spent >= c.budget))
                """)
                .param("today", today)
                .query(MAPPER)
                .list();
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> toParams(Campaign c) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userId", c.getUserId());
        params.put("title", c.getTitle());
        params.put("description", c.getDescription());
        params.put("audience", c.getAudience().getCode());
        params.put("adType", c.getAdType().getCode());
        params.put("budget", c.getBudget());
        params.put("dailyBudget", c.getDailyBudget());
        params.put("spent", c.getSpent());
        params.put("startDate", c.getStartDate());
        params.put("endDate", c.getEndDate());
        params.put("keywords", c.getKeywords().isEmpty() ? null : String.join(",", c.getKeywords()));
        params.put("status", c.getStatus().name());
        params.put("rejectionReason", c.getRejectionReason());
        params.put("flagged", c.isFlagged());
        params.put("flagReason", c.getFlagReason());
        params.put("flagCleared", c.isFlagCleared());
        params.put("peakHoursOnly", c.isPeakHoursOnly());
        params.put("qualityScore", c.getQualityScore());
        params.put("impressions", c.getImpressions());
        params.put("clicks", c.getClicks());
        params.put("conversions", c.getConversions());
        params.put("budgetAlertSent", c.isBudgetAlertSent());
        params.put("reviewedBy", c.getReviewedBy());
        params.put("reviewedAt", c.getReviewedAt());
        params.put("createdAt", c.getCreatedAt());
        params.put("updatedAt", c.getUpdatedAt());
        return params;
    }
}
