package com.adoptimizer.repository;

import com.adoptimizer.model.CodedEnum;
import com.adoptimizer.model.OptimizationLog;
import com.adoptimizer.model.OptimizationType;
import com.adoptimizer.util.SqlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OptimizationLogRepository {

    private static final String SELECT = """
            SELECT o.*, c.title AS campaign_title, u.name AS user_name
            FROM optimization_logs o
            JOIN campaigns c ON c.id = o.campaign_id
            JOIN users u ON u.id = o.user_id
            """;

    private static final RowMapper<OptimizationLog> MAPPER = (rs, rowNum) -> OptimizationLog.builder()
            .id(rs.getLong("id"))
            .campaignId(rs.getLong("campaign_id"))
            .userId(rs.getLong("user_id"))
            .type(CodedEnum.requireCode(OptimizationType.class, rs.getString("type")))
            .description(rs.getString("description"))
            .appliedAt(SqlUtils.dateTime(rs, "applied_at"))
            .campaignTitle(rs.getString("campaign_title"))
            .userName(rs.getString("user_name"))
            .build();

    private final JdbcClient jdbc;

    public void insert(OptimizationLog log) {
        jdbc.sql("""
                INSERT INTO optimization_logs (campaign_id, user_id, type, description, applied_at)
                VALUES (:campaignId, :userId, :type, :description, :appliedAt)
                """)
                .param("campaignId", log.getCampaignId())
                .param("userId", log.getUserId())
                .param("type", log.getType().getCode())
                .param("description", log.getDescription())
                .param("appliedAt", log.getAppliedAt())
                .update();
    }

    public List<OptimizationLog> findByCampaign(long campaignId) {
        return jdbc.sql(SELECT + " WHERE o.campaign_id = :id ORDER BY o.applied_at DESC, o.id DESC")
                .param("id", campaignId)
                .query(MAPPER)
                .list();
    }

    public List<OptimizationLog> findAll() {
        return jdbc.sql(SELECT + " ORDER BY o.applied_at DESC, o.id DESC").query(MAPPER).list();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM optimization_logs").query(Long.class).single();
    }
}
