package com.adoptimizer.controller.api;

import com.adoptimizer.dto.request.PasswordChangeRequest;
import com.adoptimizer.dto.request.PaymentRequest;
import com.adoptimizer.dto.request.ProfileUpdateRequest;
import com.adoptimizer.dto.request.TicketRequest;
import com.adoptimizer.dto.response.BudgetResponse;
import com.adoptimizer.dto.response.MessageResponse;
import com.adoptimizer.dto.response.NotificationListResponse;
import com.adoptimizer.dto.response.OverviewResponse;
import com.adoptimizer.dto.response.PaymentResponse;
import com.adoptimizer.dto.response.ProfileResponse;
import com.adoptimizer.dto.response.TicketResponse;
import com.adoptimizer.dto.response.WalletResponse;
import com.adoptimizer.security.AppUserPrincipal;
import com.adoptimizer.service.AnalyticsService;
import com.adoptimizer.service.NotificationService;
import com.adoptimizer.service.PaymentService;
import com.adoptimizer.service.ProfileService;
import com.adoptimizer.service.TicketService;
import com.adoptimizer.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Overview, budget, payments, notifications, profile and support for the signed-in advertiser. */
@RestController
@RequestMapping("/api/advertiser")
@RequiredArgsConstructor
public class AdvertiserAccountApiController {

    private final AnalyticsService analyticsService;
    private final WalletService walletService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final ProfileService profileService;
    private final TicketService ticketService;

    @GetMapping("/overview")
    public OverviewResponse overview(@AuthenticationPrincipal AppUserPrincipal user,
                                     @RequestParam(defaultValue = "30") int days) {
        return analyticsService.overview(user.getId(), days);
    }

    @GetMapping("/budget")
    public BudgetResponse budget(@AuthenticationPrincipal AppUserPrincipal user) {
        return analyticsService.budget(user.getId());
    }

    @GetMapping("/wallet")
    public WalletResponse wallet(@AuthenticationPrincipal AppUserPrincipal user) {
        return walletService.wallet(user.getId());
    }

    // ---- payments

    @GetMapping("/payments")
    public List<PaymentResponse> payments(@AuthenticationPrincipal AppUserPrincipal user) {
        return paymentService.history(user.getId());
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse addFunds(@AuthenticationPrincipal AppUserPrincipal user,
                                    @Valid @RequestBody PaymentRequest request) {
        return paymentService.addFunds(user.getId(), request);
    }

    // ---- notifications

    @GetMapping("/notifications")
    public NotificationListResponse notifications(@AuthenticationPrincipal AppUserPrincipal user) {
        return notificationService.list(user.getId());
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AppUserPrincipal user) {
        return Map.of("unreadCount", notificationService.unreadCount(user.getId()));
    }

    @PostMapping("/notifications/{id}/read")
    public MessageResponse markRead(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable long id) {
        notificationService.markRead(user.getId(), id);
        return new MessageResponse("Notification marked as read.");
    }

    @PostMapping("/notifications/read-all")
    public MessageResponse markAllRead(@AuthenticationPrincipal AppUserPrincipal user) {
        notificationService.markAllRead(user.getId());
        return new MessageResponse("All notifications marked as read.");
    }

    // ---- profile

    @GetMapping("/profile")
    public ProfileResponse profile(@AuthenticationPrincipal AppUserPrincipal user) {
        return profileService.profile(user.getId());
    }

    @PutMapping("/profile")
    public ProfileResponse updateProfile(@AuthenticationPrincipal AppUserPrincipal user,
                                         @Valid @RequestBody ProfileUpdateRequest request) {
        return profileService.update(user.getId(), request);
    }

    @PutMapping("/profile/password")
    public MessageResponse changePassword(@AuthenticationPrincipal AppUserPrincipal user,
                                          @Valid @RequestBody PasswordChangeRequest request) {
        profileService.changePassword(user.getId(), request);
        return new MessageResponse("Password updated.");
    }

    // ---- support

    @GetMapping("/tickets")
    public List<TicketResponse> tickets(@AuthenticationPrincipal AppUserPrincipal user) {
        return ticketService.listForUser(user.getId());
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse createTicket(@AuthenticationPrincipal AppUserPrincipal user,
                                       @Valid @RequestBody TicketRequest request) {
        return ticketService.create(user.getId(), request);
    }
}
