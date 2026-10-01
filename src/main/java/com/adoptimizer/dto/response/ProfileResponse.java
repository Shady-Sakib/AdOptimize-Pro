package com.adoptimizer.dto.response;

import com.adoptimizer.model.User;

import java.time.LocalDateTime;

public record ProfileResponse(
        Long id,
        String name,
        String email,
        String company,
        String role,
        String initial,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt) {

    public static ProfileResponse from(User user) {
        return new ProfileResponse(user.getId(), user.getName(), user.getEmail(), user.getCompany(),
                user.getRole().name().toLowerCase(), user.getInitial(), user.isActive(),
                user.getCreatedAt(), user.getLastLoginAt());
    }
}
