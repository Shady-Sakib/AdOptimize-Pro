package com.adoptimizer.dto.response;

import com.adoptimizer.model.Payment;

import java.util.Locale;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        String reference,
        BigDecimal amount,
        String method,
        String description,
        String status,
        LocalDateTime createdAt,
        Long userId,
        String userName,
        String userEmail) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), String.format(Locale.ROOT, "PAY-%06d", p.getId()), Numbers.money(p.getAmount()),
                p.getMethod(), p.getDescription(), p.getStatus(), p.getCreatedAt(),
                p.getUserId(), p.getUserName(), p.getUserEmail());
    }
}
