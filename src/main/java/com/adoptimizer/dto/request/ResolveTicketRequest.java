package com.adoptimizer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResolveTicketRequest {

    @NotBlank(message = "Write a response")
    @Size(min = 5, max = 2000, message = "Response must be 5–2000 characters")
    private String reply;
}
