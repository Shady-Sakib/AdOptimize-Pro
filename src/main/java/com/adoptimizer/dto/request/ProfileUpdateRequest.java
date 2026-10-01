package com.adoptimizer.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {

    @NotBlank(message = "Enter your full name")
    @Size(min = 2, max = 60, message = "Name must be 2–60 characters")
    @Pattern(regexp = ValidationPatterns.PERSON_NAME,
            message = "Name can only contain letters, spaces, apostrophes, periods and hyphens")
    private String name;

    @NotBlank(message = "Enter your email address")
    @Email(message = "Enter a valid email address")
    @Pattern(regexp = ValidationPatterns.EMAIL, message = "Enter a valid email address")
    @Size(max = 120, message = "Email must be at most 120 characters")
    private String email;

    @Size(max = 100, message = "Company must be at most 100 characters")
    private String company;
}
