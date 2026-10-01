package com.adoptimizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicket {

    private Long id;
    private Long userId;
    private String subject;
    private String message;
    private TicketPriority priority;
    private TicketStatus status;
    private String adminReply;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;

    /** Populated only by admin queries that join the user. */
    private String userName;
    private String userEmail;
}
