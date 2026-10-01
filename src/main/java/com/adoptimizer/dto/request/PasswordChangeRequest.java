package com.adoptimizer.dto.request;

import com.adoptimizer.validation.FieldsMatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@FieldsMatch(field = "newPassword", matchField = "confirmPassword", message = "New passwords do not match")
public class PasswordChangeRequest {

    @NotBlank(message = "Enter your current password")
    @Size(max = 72, message = "Password must be at most 72 characters")
    private String currentPassword;

    @NotBlank(message = "Enter a new password")
    @Size(min = 6, max = 72, message = "New password must be 6–72 characters")
    @Pattern(regexp = ValidationPatterns.PASSWORD, message = "New password must contain at least one letter and one number")
    private String newPassword;

    @NotBlank(message = "Confirm your new password")
    private String confirmPassword;
}
