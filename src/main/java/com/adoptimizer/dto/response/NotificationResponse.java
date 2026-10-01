package com.adoptimizer.dto.response;

import com.adoptimizer.model.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String message,
        boolean read,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType().getCode(), n.getTitle(), n.getMessage(),
                n.isRead(), n.getCreatedAt());
    }
}
