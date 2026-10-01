package com.adoptimizer.service;

import com.adoptimizer.dto.response.NotificationListResponse;
import com.adoptimizer.dto.response.NotificationResponse;
import com.adoptimizer.exception.NotFoundException;
import com.adoptimizer.model.Notification;
import com.adoptimizer.model.NotificationType;
import com.adoptimizer.repository.NotificationRepository;
import com.adoptimizer.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int LIST_LIMIT = 50;

    private final NotificationRepository notificationRepository;

    public void notify(long userId, NotificationType type, String title, String message) {
        notificationRepository.insert(Notification.builder()
                .userId(userId)
                .type(type)
                .title(truncate(title, 120))
                .message(truncate(message, 500))
                .read(false)
                .createdAt(TimeUtils.now())
                .build());
    }

    public NotificationListResponse list(long userId) {
        return new NotificationListResponse(
                notificationRepository.findByUser(userId, LIST_LIMIT).stream().map(NotificationResponse::from).toList(),
                notificationRepository.countUnread(userId));
    }

    public long unreadCount(long userId) {
        return notificationRepository.countUnread(userId);
    }

    @Transactional
    public void markRead(long userId, long notificationId) {
        if (!notificationRepository.existsForUser(notificationId, userId)) {
            throw new NotFoundException("Notification not found.");
        }
        notificationRepository.markRead(notificationId, userId);
    }

    @Transactional
    public void markAllRead(long userId) {
        notificationRepository.markAllRead(userId);
    }

    static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
