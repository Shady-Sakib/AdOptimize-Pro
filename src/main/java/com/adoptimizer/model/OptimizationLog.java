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
public class OptimizationLog {

    private Long id;
    private Long campaignId;
    private Long userId;
    private OptimizationType type;
    private String description;
    private LocalDateTime appliedAt;

    /** Populated only by report queries. */
    private String campaignTitle;
    private String userName;
}
