package com.adoptimizer.service;

import com.adoptimizer.dto.request.TicketRequest;
import com.adoptimizer.dto.response.TicketResponse;
import com.adoptimizer.exception.BadRequestException;
import com.adoptimizer.exception.NotFoundException;
import com.adoptimizer.model.CodedEnum;
import com.adoptimizer.model.NotificationType;
import com.adoptimizer.model.SupportTicket;
import com.adoptimizer.model.TicketPriority;
import com.adoptimizer.model.TicketStatus;
import com.adoptimizer.repository.TicketRepository;
import com.adoptimizer.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;

    public List<TicketResponse> listForUser(long userId) {
        return ticketRepository.findByUser(userId).stream().map(TicketResponse::from).toList();
    }

    @Transactional
    public TicketResponse create(long userId, TicketRequest request) {
        SupportTicket ticket = SupportTicket.builder()
                .userId(userId)
                .subject(request.getSubject().trim())
                .message(request.getMessage().trim())
                .priority(CodedEnum.requireCode(TicketPriority.class, request.getPriority()))
                .status(TicketStatus.OPEN)
                .createdAt(TimeUtils.now())
                .build();
        long id = ticketRepository.insert(ticket);
        return TicketResponse.from(ticketRepository.findById(id).orElseThrow());
    }

    public List<TicketResponse> listAll(String statusCode) {
        TicketStatus status = null;
        if (statusCode != null && !statusCode.isBlank() && !statusCode.equalsIgnoreCase("all")) {
            status = CodedEnum.fromCode(TicketStatus.class, statusCode)
                    .orElseThrow(() -> new BadRequestException("Unknown ticket status \"" + statusCode + "\"."));
        }
        return ticketRepository.findAll(status).stream().map(TicketResponse::from).toList();
    }

    @Transactional
    public TicketResponse resolve(long adminId, long ticketId, String reply) {
        SupportTicket ticket = find(ticketId);
        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw new BadRequestException("This ticket is already resolved.");
        }
        ticketRepository.resolve(ticketId, reply.trim(), adminId, TimeUtils.now());
        notificationService.notify(ticket.getUserId(), NotificationType.SUCCESS, "Support ticket resolved",
                "Your ticket \"" + ticket.getSubject() + "\" was answered. Open Support to read the reply.");
        return TicketResponse.from(find(ticketId));
    }

    @Transactional
    public TicketResponse reopen(long ticketId) {
        SupportTicket ticket = find(ticketId);
        if (ticket.getStatus() != TicketStatus.RESOLVED) {
            throw new BadRequestException("This ticket is already open.");
        }
        ticketRepository.reopen(ticketId);
        notificationService.notify(ticket.getUserId(), NotificationType.INFO, "Support ticket reopened",
                "Your ticket \"" + ticket.getSubject() + "\" was reopened by our support team.");
        return TicketResponse.from(find(ticketId));
    }

    private SupportTicket find(long ticketId) {
        return ticketRepository.findById(ticketId).orElseThrow(() -> new NotFoundException("Ticket not found."));
    }
}
