package com.adoptimizer.repository;

import com.adoptimizer.model.CodedEnum;
import com.adoptimizer.model.SupportTicket;
import com.adoptimizer.model.TicketPriority;
import com.adoptimizer.model.TicketStatus;
import com.adoptimizer.util.SqlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TicketRepository {

    private static final String SELECT = """
            SELECT t.*, u.name AS user_name, u.email AS user_email
            FROM support_tickets t
            JOIN users u ON u.id = t.user_id
            """;

    /** High priority first, then newest. */
    private static final String ORDER = """
             ORDER BY CASE t.status WHEN 'open' THEN 0 ELSE 1 END,
                      CASE t.priority WHEN 'high' THEN 0 WHEN 'medium' THEN 1 ELSE 2 END,
                      t.created_at DESC, t.id DESC
            """;

    private static final RowMapper<SupportTicket> MAPPER = (rs, rowNum) -> SupportTicket.builder()
            .id(rs.getLong("id"))
            .userId(rs.getLong("user_id"))
            .subject(rs.getString("subject"))
            .message(rs.getString("message"))
            .priority(CodedEnum.requireCode(TicketPriority.class, rs.getString("priority")))
            .status(CodedEnum.requireCode(TicketStatus.class, rs.getString("status")))
            .adminReply(rs.getString("admin_reply"))
            .resolvedBy(SqlUtils.nullableLong(rs, "resolved_by"))
            .resolvedAt(SqlUtils.dateTime(rs, "resolved_at"))
            .createdAt(SqlUtils.dateTime(rs, "created_at"))
            .userName(rs.getString("user_name"))
            .userEmail(rs.getString("user_email"))
            .build();

    private final JdbcClient jdbc;

    public long insert(SupportTicket t) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO support_tickets (user_id, subject, message, priority, status, admin_reply,
                    resolved_by, resolved_at, created_at)
                VALUES (:userId, :subject, :message, :priority, :status, :adminReply, :resolvedBy, :resolvedAt, :createdAt)
                """)
                .param("userId", t.getUserId())
                .param("subject", t.getSubject())
                .param("message", t.getMessage())
                .param("priority", t.getPriority().getCode())
                .param("status", t.getStatus().getCode())
                .param("adminReply", t.getAdminReply())
                .param("resolvedBy", t.getResolvedBy())
                .param("resolvedAt", t.getResolvedAt())
                .param("createdAt", t.getCreatedAt())
                .update(keys, "id");
        return Objects.requireNonNull(keys.getKey(), "No generated key for ticket").longValue();
    }

    public Optional<SupportTicket> findById(long id) {
        return jdbc.sql(SELECT + " WHERE t.id = :id").param("id", id).query(MAPPER).optional();
    }

    public List<SupportTicket> findByUser(long userId) {
        return jdbc.sql(SELECT + " WHERE t.user_id = :userId" + ORDER)
                .param("userId", userId)
                .query(MAPPER)
                .list();
    }

    public List<SupportTicket> findAll(TicketStatus status) {
        String sql = SELECT + (status != null ? " WHERE t.status = :status" : "") + ORDER;
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        if (status != null) {
            spec = spec.param("status", status.getCode());
        }
        return spec.query(MAPPER).list();
    }

    public boolean resolve(long id, String reply, long adminId, LocalDateTime now) {
        return jdbc.sql("""
                UPDATE support_tickets SET status = 'resolved', admin_reply = :reply, resolved_by = :adminId,
                    resolved_at = :now
                WHERE id = :id AND status = 'open'
                """)
                .param("reply", reply)
                .param("adminId", adminId)
                .param("now", now)
                .param("id", id)
                .update() == 1;
    }

    public boolean reopen(long id) {
        return jdbc.sql("UPDATE support_tickets SET status = 'open', resolved_at = NULL, resolved_by = NULL WHERE id = :id AND status = 'resolved'")
                .param("id", id)
                .update() == 1;
    }

    public long countOpen() {
        return jdbc.sql("SELECT COUNT(*) FROM support_tickets WHERE status = 'open'").query(Long.class).single();
    }
}
