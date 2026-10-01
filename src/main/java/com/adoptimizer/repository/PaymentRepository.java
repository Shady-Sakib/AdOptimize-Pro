package com.adoptimizer.repository;

import com.adoptimizer.model.Payment;
import com.adoptimizer.util.SqlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class PaymentRepository {

    private static final String SELECT = """
            SELECT p.*, u.name AS user_name, u.email AS user_email
            FROM payments p
            JOIN users u ON u.id = p.user_id
            """;

    private static final RowMapper<Payment> MAPPER = (rs, rowNum) -> Payment.builder()
            .id(rs.getLong("id"))
            .userId(rs.getLong("user_id"))
            .amount(rs.getBigDecimal("amount"))
            .cardBrand(rs.getString("card_brand"))
            .cardLast4(rs.getString("card_last4"))
            .description(rs.getString("description"))
            .status(rs.getString("status"))
            .createdAt(SqlUtils.dateTime(rs, "created_at"))
            .userName(rs.getString("user_name"))
            .userEmail(rs.getString("user_email"))
            .build();

    private final JdbcClient jdbc;

    public long insert(Payment payment) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO payments (user_id, amount, card_brand, card_last4, description, status, created_at)
                VALUES (:userId, :amount, :brand, :last4, :description, :status, :createdAt)
                """)
                .param("userId", payment.getUserId())
                .param("amount", payment.getAmount())
                .param("brand", payment.getCardBrand())
                .param("last4", payment.getCardLast4())
                .param("description", payment.getDescription())
                .param("status", payment.getStatus())
                .param("createdAt", payment.getCreatedAt())
                .update(keys, "id");
        return Objects.requireNonNull(keys.getKey(), "No generated key for payment").longValue();
    }

    public List<Payment> findByUser(long userId, int limit) {
        return jdbc.sql(SELECT + " WHERE p.user_id = :userId ORDER BY p.created_at DESC, p.id DESC LIMIT :limit")
                .param("userId", userId)
                .param("limit", limit)
                .query(MAPPER)
                .list();
    }

    public List<Payment> findAll() {
        return jdbc.sql(SELECT + " ORDER BY p.created_at DESC, p.id DESC").query(MAPPER).list();
    }

    public BigDecimal sumCompletedByUser(long userId) {
        return jdbc.sql("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE user_id = :userId AND status = 'completed'")
                .param("userId", userId)
                .query(BigDecimal.class)
                .single();
    }

    public BigDecimal sumCompleted() {
        return jdbc.sql("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'completed'")
                .query(BigDecimal.class)
                .single();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM payments").query(Long.class).single();
    }

    public Map<YearMonth, BigDecimal> monthlyTotals(LocalDateTime from) {
        Map<YearMonth, BigDecimal> result = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT YEAR(created_at) AS y, MONTH(created_at) AS m, SUM(amount) AS total
                FROM payments
                WHERE status = 'completed' AND created_at >= :from
                GROUP BY YEAR(created_at), MONTH(created_at)
                """)
                .param("from", from)
                .query((rs, rowNum) -> {
                    YearMonth month = YearMonth.of(rs.getInt("y"), rs.getInt("m"));
                    result.put(month, rs.getBigDecimal("total"));
                    return month;
                })
                .list();
        return result;
    }
}
