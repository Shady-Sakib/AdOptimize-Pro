package com.adoptimizer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Admin sign-up: same rules as advertisers plus the group authorization code. */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminRegisterRequest extends RegisterRequest {

    @NotBlank(message = "Enter the group code")
    @Size(max = 50, message = "Group code must be at most 50 characters")
    private String groupCode;
}
