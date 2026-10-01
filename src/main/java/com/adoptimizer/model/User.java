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
public class User {

    private Long id;
    private String name;
    private String email;
    private String passwordHash;
    private String company;
    private Role role;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    /** First letter of the name, used for avatars. */
    public String getInitial() {
        return (name == null || name.isBlank()) ? "?" : name.trim().substring(0, 1).toUpperCase();
    }
}
