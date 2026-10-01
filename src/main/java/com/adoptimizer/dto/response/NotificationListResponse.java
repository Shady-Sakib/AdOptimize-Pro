package com.adoptimizer.dto.response;

import java.util.List;

public record NotificationListResponse(List<NotificationResponse> items, long unreadCount) {
}
