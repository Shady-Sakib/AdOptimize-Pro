package com.adoptimizer.repository;

import com.adoptimizer.model.DailyStat;
import com.adoptimizer.util.SqlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class DailyStatRepository {

    private static final RowMapper<DailyStat> TOTALS_BY_DATE = (rs, rowNum) -> DailyStat.builder()
            .statDate(SqlUtils.date(rs, "stat_date"))
            .impressions(rs.getLong("impressions"))
            .clicks(rs.getLong("clicks"))
            .conversions(rs.getLong("conversions"))
            .spend(rs.getBigDecimal("spend"))
            .build();

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    /** Adds delivery to the row for that campaign and day, creating the row when needed. */
    public void addDelivery(long campaignId, LocalDate date, long impressions, long clicks, long conversions, BigDecimal spend) {
        jdbc.sql("""
                INSERT INTO campaign_daily_stats (campaign_id, stat_date, impressions, clicks, conversions, spend)
                VALUES (:campaignId, :date, :impressions, :clicks, :conversions, :spend)
                ON DUPLICATE KEY UPDATE
                    impressions = impressions + :impressions,
                    clicks = clicks + :clicks,
                    conversions = conversions + :conversions,
                    spend = spend + :spend
                """)
                .param("campaignId", campaignId)
                .param("date", date)
                .param("impressions", impressions)
                .param("clicks", clicks)
                .param("conversions", conversions)
                .param("spend", spend)
                .update();
    }

    /** Bulk insert used by the demo data seeder. */
    public void insertAll(List<DailyStat> stats) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO campaign_daily_stats (campaign_id, stat_date, impressions, clicks, conversions, spend)
                VALUES (?, ?, ?, ?, ?, ?)
                """, stats, 200, (ps, stat) -> {
            ps.setLong(1, stat.getCampaignId());
            ps.setDate(2, Date.valueOf(stat.getStatDate()));
            ps.setLong(3, stat.getImpressions());
            ps.setLong(4, stat.getClicks());
            ps.setLong(5, stat.getConversions());
            ps.setBigDecimal(6, stat.getSpend());
        });
    }

    public BigDecimal spendOn(long campaignId, LocalDate date) {
        return jdbc.sql("SELECT COALESCE(SUM(spend), 0) FROM campaign_daily_stats WHERE campaign_id = :id AND stat_date = :date")
                .param("id", campaignId)
                .param("date", date)
                .query(BigDecimal.class)
                .single();
    }

    /** Per-day totals across all of an advertiser's (non-deleted) campaigns. */
    public List<DailyStat> dailyTotalsForUser(long userId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                SELECT s.stat_date, SUM(s.impressions) AS impressions, SUM(s.clicks) AS clicks,
                       SUM(s.conversions) AS conversions, SUM(s.spend) AS spend
                FROM campaign_daily_stats s
                JOIN campaigns c ON c.id = s.campaign_id
                WHERE c.user_id = :userId AND c.deleted_at IS NULL AND s.stat_date BETWEEN :from AND :to
                GROUP BY s.stat_date
                ORDER BY s.stat_date
                """)
                .param("userId", userId)
                .param("from", from)
                .param("to", to)
                .query(TOTALS_BY_DATE)
                .list();
    }

    public List<DailyStat> dailyTotalsForCampaign(long campaignId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                SELECT stat_date, impressions, clicks, conversions, spend
                FROM campaign_daily_stats
                WHERE campaign_id = :id AND stat_date BETWEEN :from AND :to
                ORDER BY stat_date
                """)
                .param("id", campaignId)
                .param("from", from)
                .param("to", to)
                .query(TOTALS_BY_DATE)
                .list();
    }

    /** Monthly totals starting at {@code from}; {@code userId == null} means the whole platform. */
    public Map<YearMonth, DailyStat> monthlyTotals(Long userId, LocalDate from) {
        String sql = """
                SELECT YEAR(s.stat_date) AS y, MONTH(s.stat_date) AS m,
                       SUM(s.impressions) AS impressions, SUM(s.clicks) AS clicks,
                       SUM(s.conversions) AS conversions, SUM(s.spend) AS spend
                FROM campaign_daily_stats s
                JOIN campaigns c ON c.id = s.campaign_id
                WHERE s.stat_date >= :from
                """
                + (userId != null ? " AND c.user_id = :userId AND c.deleted_at IS NULL" : "")
                + " GROUP BY YEAR(s.stat_date), MONTH(s.stat_date)";
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("from", from);
        if (userId != null) {
            spec = spec.param("userId", userId);
        }
        Map<YearMonth, DailyStat> result = new LinkedHashMap<>();
        spec.query((rs, rowNum) -> {
            YearMonth month = YearMonth.of(rs.getInt("y"), rs.getInt("m"));
            DailyStat stat = DailyStat.builder()
                    .impressions(rs.getLong("impressions"))
                    .clicks(rs.getLong("clicks"))
                    .conversions(rs.getLong("conversions"))
                    .spend(rs.getBigDecimal("spend"))
                    .build();
            result.put(month, stat);
            return month;
        }).list();
        return result;
    }
}
