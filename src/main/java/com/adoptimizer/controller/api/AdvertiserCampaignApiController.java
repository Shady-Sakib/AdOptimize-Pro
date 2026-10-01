package com.adoptimizer.controller.api;

import com.adoptimizer.dto.request.ApplyOptimizationRequest;
import com.adoptimizer.dto.request.CampaignRequest;
import com.adoptimizer.dto.response.CampaignDetailResponse;
import com.adoptimizer.dto.response.CampaignOptionsResponse;
import com.adoptimizer.dto.response.CampaignResponse;
import com.adoptimizer.dto.response.MessageResponse;
import com.adoptimizer.dto.response.OptimizationResponse;
import com.adoptimizer.security.AppUserPrincipal;
import com.adoptimizer.service.CampaignService;
import com.adoptimizer.service.OptimizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/** Campaign management and optimization for the signed-in advertiser. */
@RestController
@RequestMapping("/api/advertiser/campaigns")
@RequiredArgsConstructor
public class AdvertiserCampaignApiController {

    private final CampaignService campaignService;
    private final OptimizationService optimizationService;

    @GetMapping
    public List<CampaignResponse> list(@AuthenticationPrincipal AppUserPrincipal user,
                                       @RequestParam(defaultValue = "all") String status) {
        return campaignService.listForUser(user.getId(), status);
    }

    @GetMapping("/options")
    public CampaignOptionsResponse options(@AuthenticationPrincipal AppUserPrincipal user) {
        return campaignService.options(user.getId());
    }

    @GetMapping("/optimizable")
    public List<CampaignResponse> optimizable(@AuthenticationPrincipal AppUserPrincipal user) {
        return optimizationService.optimizableCampaigns(user.getId());
    }

    @GetMapping("/{id}")
    public CampaignDetailResponse detail(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable long id) {
        return campaignService.detail(user.getId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignResponse create(@AuthenticationPrincipal AppUserPrincipal user,
                                   @Valid @RequestBody CampaignRequest request) {
        return campaignService.create(user.getId(), request);
    }

    @PutMapping("/{id}")
    public CampaignResponse update(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable long id,
                                   @Valid @RequestBody CampaignRequest request) {
        return campaignService.update(user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable long id) {
        return campaignService.delete(user.getId(), id);
    }

    @PostMapping("/{id}/pause")
    public CampaignResponse pause(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable long id) {
        return campaignService.pause(user.getId(), id);
    }

    @PostMapping("/{id}/resume")
    public CampaignResponse resume(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable long id) {
        return campaignService.resume(user.getId(), id);
    }

    @GetMapping("/{id}/optimization")
    public OptimizationResponse optimization(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable long id) {
        return optimizationService.analyze(user.getId(), id);
    }

    @PostMapping("/{id}/optimization")
    public OptimizationResponse applyOptimization(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable long id,
                                                  @Valid @RequestBody ApplyOptimizationRequest request) {
        return optimizationService.apply(user.getId(), id, request.getType());
    }
}
