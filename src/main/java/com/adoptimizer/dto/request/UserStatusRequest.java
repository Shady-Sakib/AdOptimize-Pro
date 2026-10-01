package com.adoptimizer.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserStatusRequest {

    @NotNull(message = "Choose whether the account is active")
    private Boolean active;
}
