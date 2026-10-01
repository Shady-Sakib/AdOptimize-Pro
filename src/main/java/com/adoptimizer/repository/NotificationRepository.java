package com.adoptimizer.repository;

import com.adoptimizer.model.CodedEnum;
import com.adoptimizer.model.Notification;
import com.adoptimizer.model.NotificationType;
import com.adoptimizer.util.SqlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificationRepository {

    private static final RowMapper<Notification> MAPPER = (rs, rowNum) -> Notification.builder()
            .id(rs.getLong("id"))
            .userId(rs.getLong("user_id"))
            .type(CodedEnum.requireCode(NotificationType.class, rs.getString("type")))
            .title(rs.getString("title"))
            .message(rs.getString("message"))
            .read(rs.getBoolean("is_read"))
            .createdAt(SqlUtils.dateTime(rs, "created_at"))
            .build();

    private final JdbcClient jdbc;

    public void insert(Notification n) {
        jdbc.sql("""
                INSERT INTO notifications (user_id, type, title, message, is_read, created_at)
                VALUES (:userId, :type, :title, :message, :read, :createdAt)
                """)
                .param("userId", n.getUserId())
                .param("type", n.getType().getCode())
                .param("title", n.getTitle())
                .param("message", n.getMessage())
                .param("read", n.isRead())
                .param("createdAt", n.getCreatedAt())
                .update();
    }

    public List<Notification> findByUser(long userId, int limit) {
        return jdbc.sql("SELECT * FROM notifications WHERE user_id = :userId ORDER BY created_at DESC, id DESC LIMIT :limit")
                .param("userId", userId)
                .param("limit", limit)
                .query(MAPPER)
                .list();
    }

    public long countUnread(long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM notifications WHERE user_id = :userId AND is_read = FALSE")
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    public int markRead(long id, long userId) {
        return jdbc.sql("UPDATE notifications SET is_read = TRUE WHERE id = :id AND user_id = :userId")
                .param("id", id)
                .param("userId", userId)
                .update();
    }

    public boolean existsForUser(long id, long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM notifications WHERE id = :id AND user_id = :userId")
                .param("id", id)
                .param("userId", userId)
                .query(Long.class)
                .single() > 0;
    }

    public void markAllRead(long userId) {
        jdbc.sql("UPDATE notifications SET is_read = TRUE WHERE user_id = :userId AND is_read = FALSE")
                .param("userId", userId)
                .update();
    }
}
