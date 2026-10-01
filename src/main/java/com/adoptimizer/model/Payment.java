package com.adoptimizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    public static final String STATUS_COMPLETED = "completed";

    private Long id;
    private Long userId;
    private BigDecimal amount;
    private String cardBrand;
    private String cardLast4;
    private String description;
    private String status;
    private LocalDateTime createdAt;

    /** Populated only by admin queries that join the user. */
    private String userName;
    private String userEmail;

    public String getMethod() {
        return cardBrand + " ****" + cardLast4;
    }
}
