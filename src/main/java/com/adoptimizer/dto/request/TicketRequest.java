package com.adoptimizer.dto.request;

import com.adoptimizer.model.TicketPriority;
import com.adoptimizer.validation.EnumCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TicketRequest {

    @NotBlank(message = "Enter a subject")
    @Size(min = 5, max = 120, message = "Subject must be 5–120 characters")
    private String subject;

    @NotBlank(message = "Select a priority")
    @EnumCode(enumClass = TicketPriority.class, message = "Select a valid priority")
    private String priority;

    @NotBlank(message = "Describe your issue")
    @Size(min = 10, max = 2000, message = "Message must be 10–2000 characters")
    private String message;
}
