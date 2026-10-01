package com.adoptimizer.dto.request;

import com.adoptimizer.validation.FieldsMatch;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@FieldsMatch(field = "password", matchField = "confirmPassword", message = "Passwords do not match")
public class RegisterRequest {

    @NotBlank(message = "Enter your full name")
    @Size(min = 2, max = 60, message = "Name must be 2–60 characters")
    @Pattern(regexp = ValidationPatterns.PERSON_NAME,
            message = "Name can only contain letters, spaces, apostrophes, periods and hyphens")
    private String name;

    @Size(max = 100, message = "Company must be at most 100 characters")
    private String company;

    @NotBlank(message = "Enter your email address")
    @Email(message = "Enter a valid email address")
    @Pattern(regexp = ValidationPatterns.EMAIL, message = "Enter a valid email address")
    @Size(max = 120, message = "Email must be at most 120 characters")
    private String email;

    @NotBlank(message = "Enter a password")
    @Size(min = 6, max = 72, message = "Password must be 6–72 characters")
    @Pattern(regexp = ValidationPatterns.PASSWORD, message = "Password must contain at least one letter and one number")
    private String password;

    @NotBlank(message = "Confirm your password")
    private String confirmPassword;
}
