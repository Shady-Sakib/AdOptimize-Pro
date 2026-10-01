package com.adoptimizer.controller.api;

import com.adoptimizer.dto.request.RejectCampaignRequest;
import com.adoptimizer.dto.request.ResolveTicketRequest;
import com.adoptimizer.dto.request.SettingsRequest;
import com.adoptimizer.dto.request.UserStatusRequest;
import com.adoptimizer.dto.response.AdminDashboardResponse;
import com.adoptimizer.dto.response.AdminUserDetailResponse;
import com.adoptimizer.dto.response.AdminUserResponse;
import com.adoptimizer.dto.response.AnalyticsResponse;
import com.adoptimizer.dto.response.CampaignResponse;
import com.adoptimizer.dto.response.MessageResponse;
import com.adoptimizer.dto.response.ModerationResponse;
import com.adoptimizer.dto.response.ReportSummaryResponse;
import com.adoptimizer.dto.response.RevenueResponse;
import com.adoptimizer.dto.response.SettingsResponse;
import com.adoptimizer.dto.response.SimulationRunResponse;
import com.adoptimizer.dto.response.TicketResponse;
import com.adoptimizer.security.AppUserPrincipal;
import com.adoptimizer.service.AdminUserService;
import com.adoptimizer.service.AnalyticsService;
import com.adoptimizer.service.CampaignService;
import com.adoptimizer.service.ModerationService;
import com.adoptimizer.service.ReportService;
import com.adoptimizer.service.SettingsService;
import com.adoptimizer.service.SimulationService;
import com.adoptimizer.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Admin panel API: platform overview, users, ad review, moderation, finance, reports, support and settings. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final AnalyticsService analyticsService;
    private final AdminUserService adminUserService;
    private final CampaignService campaignService;
    private final ModerationService moderationService;
    private final TicketService ticketService;
    private final SettingsService settingsService;
    private final ReportService reportService;
    private final SimulationService simulationService;

    // ---- overview

    @GetMapping("/dashboard")
    public AdminDashboardResponse dashboard() {
        return analyticsService.adminDashboard();
    }

    @GetMapping("/analytics")
    public AnalyticsResponse analytics() {
        return analyticsService.platformAnalytics();
    }

    @GetMapping("/revenue")
    public RevenueResponse revenue() {
        return analyticsService.revenue();
    }

    // ---- users

    @GetMapping("/users")
    public List<AdminUserResponse> users(@RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "all") String status) {
        return adminUserService.list(q, status);
    }

    @GetMapping("/users/{id}")
    public AdminUserDetailResponse user(@PathVariable long id) {
        return adminUserService.detail(id);
    }

    @PatchMapping("/users/{id}/status")
    public AdminUserResponse setUserStatus(@AuthenticationPrincipal AppUserPrincipal admin, @PathVariable long id,
                                           @Valid @RequestBody UserStatusRequest request) {
        return adminUserService.setActive(admin.getId(), id, request.getActive());
    }

    // ---- campaigns

    @GetMapping("/campaigns")
    public List<CampaignResponse> campaigns(@RequestParam(defaultValue = "all") String status) {
        return campaignService.listAll(status);
    }

    @PostMapping("/campaigns/{id}/approve")
    public CampaignResponse approve(@AuthenticationPrincipal AppUserPrincipal admin, @PathVariable long id) {
        return campaignService.approve(admin.getId(), id);
    }

    @PostMapping("/campaigns/{id}/reject")
    public CampaignResponse reject(@AuthenticationPrincipal AppUserPrincipal admin, @PathVariable long id,
                                   @Valid @RequestBody RejectCampaignRequest request) {
        return campaignService.reject(admin.getId(), id, request.getReason());
    }

    @DeleteMapping("/campaigns/{id}")
    public MessageResponse remove(@PathVariable long id) {
        return campaignService.remove(id);
    }

    // ---- moderation

    @GetMapping("/moderation")
    public ModerationResponse moderation() {
        return moderationService.overview();
    }

    @PostMapping("/campaigns/{id}/clear-flag")
    public MessageResponse clearFlag(@PathVariable long id) {
        moderationService.clearFlag(id);
        return new MessageResponse("Flag cleared. The campaign can now be reviewed normally.");
    }

    @PostMapping("/moderation/scan")
    public MessageResponse scan() {
        int flagged = moderationService.rescanAll();
        return new MessageResponse("Scan complete: " + flagged + " campaign" + (flagged == 1 ? "" : "s") + " flagged for review.");
    }

    // ---- support

    @GetMapping("/tickets")
    public List<TicketResponse> tickets(@RequestParam(defaultValue = "all") String status) {
        return ticketService.listAll(status);
    }

    @PostMapping("/tickets/{id}/resolve")
    public TicketResponse resolve(@AuthenticationPrincipal AppUserPrincipal admin, @PathVariable long id,
                                  @Valid @RequestBody ResolveTicketRequest request) {
        return ticketService.resolve(admin.getId(), id, request.getReply());
    }

    @PostMapping("/tickets/{id}/reopen")
    public TicketResponse reopen(@PathVariable long id) {
        return ticketService.reopen(id);
    }

    // ---- settings and simulator

    @GetMapping("/settings")
    public SettingsResponse settings() {
        return SettingsResponse.from(settingsService.get());
    }

    @PutMapping("/settings")
    public SettingsResponse updateSettings(@Valid @RequestBody SettingsRequest request) {
        return SettingsResponse.from(settingsService.update(request));
    }

    @PostMapping("/simulation/run")
    public SimulationRunResponse runSimulation() {
        return simulationService.runOnce();
    }

    // ---- reports

    @GetMapping("/reports/summary")
    public ReportSummaryResponse reportSummary() {
        return reportService.summary();
    }

    @GetMapping("/reports/{type}")
    public ResponseEntity<byte[]> report(@PathVariable String type) {
        byte[] body = reportService.generate(type).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(reportService.fileName(type.toLowerCase())).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
