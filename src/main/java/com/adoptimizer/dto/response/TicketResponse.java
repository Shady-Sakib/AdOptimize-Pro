package com.adoptimizer.dto.response;

import com.adoptimizer.model.SupportTicket;

import java.util.Locale;
import java.time.LocalDateTime;

public record TicketResponse(
        Long id,
        String reference,
        String subject,
        String message,
        String priority,
        String status,
        String adminReply,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt,
        Long userId,
        String userName,
        String userEmail) {

    public static TicketResponse from(SupportTicket t) {
        return new TicketResponse(t.getId(), String.format(Locale.ROOT, "TKT-%05d", t.getId()), t.getSubject(), t.getMessage(),
                t.getPriority().getCode(), t.getStatus().getCode(), t.getAdminReply(), t.getResolvedAt(),
                t.getCreatedAt(), t.getUserId(), t.getUserName(), t.getUserEmail());
    }
}
