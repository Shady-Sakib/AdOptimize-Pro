package com.adoptimizer.service;

import com.adoptimizer.dto.response.AdminUserDetailResponse;
import com.adoptimizer.dto.response.AdminUserResponse;
import com.adoptimizer.dto.response.CampaignResponse;
import com.adoptimizer.dto.response.PaymentResponse;
import com.adoptimizer.dto.response.TicketResponse;
import com.adoptimizer.exception.BadRequestException;
import com.adoptimizer.exception.NotFoundException;
import com.adoptimizer.model.NotificationType;
import com.adoptimizer.model.Role;
import com.adoptimizer.model.User;
import com.adoptimizer.repository.CampaignRepository;
import com.adoptimizer.repository.PaymentRepository;
import com.adoptimizer.repository.TicketRepository;
import com.adoptimizer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final CampaignRepository campaignRepository;
    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;

    public List<AdminUserResponse> list(String search, String status) {
        Boolean active = switch (status == null ? "all" : status.toLowerCase()) {
            case "all" -> null;
            case "active" -> Boolean.TRUE;
            case "inactive" -> Boolean.FALSE;
            default -> throw new BadRequestException("Unknown user status filter \"" + status + "\".");
        };
        if (search != null && search.length() > 100) {
            throw new BadRequestException("Search text must be at most 100 characters.");
        }
        return userRepository.findAdvertiserSummaries(search, active);
    }

    public AdminUserDetailResponse detail(long userId) {
        AdminUserResponse summary = userRepository.findAdvertiserSummary(userId)
                .orElseThrow(() -> new NotFoundException("Advertiser not found."));
        return new AdminUserDetailResponse(summary, walletService.wallet(userId),
                campaignRepository.findByUser(userId, null).stream().map(CampaignResponse::from).toList(),
                paymentRepository.findByUser(userId, 10).stream().map(PaymentResponse::from).toList(),
                ticketRepository.findByUser(userId).stream().map(TicketResponse::from).toList());
    }

    @Transactional
    public AdminUserResponse setActive(long adminId, long userId, boolean active) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Advertiser not found."));
        if (user.getId() == adminId) {
            throw new BadRequestException("You can't deactivate your own account.");
        }
        if (user.getRole() != Role.ADVERTISER) {
            throw new BadRequestException("Only advertiser accounts can be managed here.");
        }
        if (user.isActive() != active) {
            userRepository.updateActive(userId, active);
            if (active) {
                notificationService.notify(userId, NotificationType.SUCCESS, "Account reactivated",
                        "Your advertiser account was reactivated by an administrator. Welcome back!");
            }
        }
        return userRepository.findAdvertiserSummary(userId).orElseThrow();
    }
}
