package com.adoptimizer.repository;

import com.adoptimizer.dto.response.AdminUserResponse;
import com.adoptimizer.dto.response.Numbers;
import com.adoptimizer.model.Role;
import com.adoptimizer.model.User;
import com.adoptimizer.util.SqlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private static final RowMapper<User> USER_MAPPER = (rs, rowNum) -> User.builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .email(rs.getString("email"))
            .passwordHash(rs.getString("password_hash"))
            .company(rs.getString("company"))
            .role(Role.valueOf(rs.getString("role")))
            .active(rs.getBoolean("active"))
            .createdAt(SqlUtils.dateTime(rs, "created_at"))
            .lastLoginAt(SqlUtils.dateTime(rs, "last_login_at"))
            .build();

    private final JdbcClient jdbc;

    public Optional<User> findById(long id) {
        return jdbc.sql("SELECT * FROM users WHERE id = :id")
                .param("id", id)
                .query(USER_MAPPER)
                .optional();
    }

    public Optional<User> findByEmail(String email) {
        return jdbc.sql("SELECT * FROM users WHERE email = :email")
                .param("email", normalizeEmail(email))
                .query(USER_MAPPER)
                .optional();
    }

    public boolean existsByEmail(String email) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE email = :email")
                .param("email", normalizeEmail(email))
                .query(Long.class)
                .single() > 0;
    }

    public boolean existsByEmailForOtherUser(String email, long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE email = :email AND id <> :id")
                .param("email", normalizeEmail(email))
                .param("id", userId)
                .query(Long.class)
                .single() > 0;
    }

    public long insert(User user) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO users (name, email, password_hash, company, role, active, created_at)
                VALUES (:name, :email, :passwordHash, :company, :role, :active, :createdAt)
                """)
                .param("name", user.getName())
                .param("email", normalizeEmail(user.getEmail()))
                .param("passwordHash", user.getPasswordHash())
                .param("company", user.getCompany())
                .param("role", user.getRole().name())
                .param("active", user.isActive())
                .param("createdAt", user.getCreatedAt())
                .update(keys);
        return Objects.requireNonNull(keys.getKey(), "No generated key for user").longValue();
    }

    public void updateProfile(long id, String name, String email, String company) {
        jdbc.sql("UPDATE users SET name = :name, email = :email, company = :company WHERE id = :id")
                .param("name", name)
                .param("email", normalizeEmail(email))
                .param("company", company)
                .param("id", id)
                .update();
    }

    public void updatePassword(long id, String passwordHash) {
        jdbc.sql("UPDATE users SET password_hash = :hash WHERE id = :id")
                .param("hash", passwordHash)
                .param("id", id)
                .update();
    }

    public void updateActive(long id, boolean active) {
        jdbc.sql("UPDATE users SET active = :active WHERE id = :id")
                .param("active", active)
                .param("id", id)
                .update();
    }

    public void updateLastLogin(long id, LocalDateTime time) {
        jdbc.sql("UPDATE users SET last_login_at = :time WHERE id = :id")
                .param("time", time)
                .param("id", id)
                .update();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM users").query(Long.class).single();
    }

    public long countByRole(Role role) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE role = :role")
                .param("role", role.name())
                .query(Long.class)
                .single();
    }

    public long countActiveByRole(Role role) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE role = :role AND active = TRUE")
                .param("role", role.name())
                .query(Long.class)
                .single();
    }

    public List<Long> findActiveAdminIds() {
        return jdbc.sql("SELECT id FROM users WHERE role = 'ADMIN' AND active = TRUE")
                .query(Long.class)
                .list();
    }

    /**
     * Advertisers with campaign count and total spend, optionally filtered by a search term
     * (name, email or company) and by active flag.
     */
    public List<AdminUserResponse> findAdvertiserSummaries(String search, Boolean active) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.id, u.name, u.email, u.company, u.active, u.created_at, u.last_login_at,
                       COUNT(c.id) AS campaign_count,
                       COALESCE(SUM(c.spent), 0) AS total_spent
                FROM users u
                LEFT JOIN campaigns c ON c.user_id = u.id AND c.deleted_at IS NULL
                WHERE u.role = 'ADVERTISER'
                """);
        Map<String, Object> params = new HashMap<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(u.name) LIKE :q OR LOWER(u.email) LIKE :q OR LOWER(COALESCE(u.company, '')) LIKE :q)");
            params.put("q", SqlUtils.containsPattern(search.trim()));
        }
        if (active != null) {
            sql.append(" AND u.active = :active");
            params.put("active", active);
        }
        sql.append(" GROUP BY u.id, u.name, u.email, u.company, u.active, u.created_at, u.last_login_at");
        sql.append(" ORDER BY u.created_at DESC, u.id DESC");

        return jdbc.sql(sql.toString())
                .params(params)
                .query((rs, rowNum) -> {
                    String name = rs.getString("name");
                    return new AdminUserResponse(
                            rs.getLong("id"),
                            name,
                            rs.getString("email"),
                            rs.getString("company"),
                            name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(),
                            rs.getBoolean("active"),
                            SqlUtils.dateTime(rs, "created_at"),
                            SqlUtils.dateTime(rs, "last_login_at"),
                            rs.getLong("campaign_count"),
                            Numbers.money(rs.getBigDecimal("total_spent")));
                })
                .list();
    }

    public Optional<AdminUserResponse> findAdvertiserSummary(long userId) {
        return findAdvertiserSummaries(null, null).stream()
                .filter(user -> user.id() == userId)
                .findFirst();
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
