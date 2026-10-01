package com.adoptimizer.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String name,
        String email,
        String company,
        String initial,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        long campaignCount,
        BigDecimal totalSpent) {
}
