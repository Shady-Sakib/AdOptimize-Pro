package com.adoptimizer.dto.response;

import java.util.List;

public record AdminUserDetailResponse(
        AdminUserResponse user,
        WalletResponse wallet,
        List<CampaignResponse> campaigns,
        List<PaymentResponse> payments,
        List<TicketResponse> tickets) {
}
