package com.adoptimizer.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectCampaignRequest {

    /** Optional; a default reason is used when blank. */
    @Size(max = 300, message = "Reason must be at most 300 characters")
    private String reason;
}
