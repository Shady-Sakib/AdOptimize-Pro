package com.adoptimizer.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.YearMonth;

public class CardExpiryValidator implements ConstraintValidator<CardExpiry, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // @NotBlank reports missing values
        }
        return isValidExpiry(value, YearMonth.now());
    }

    public static boolean isValidExpiry(String value, YearMonth now) {
        String trimmed = value.trim();
        if (!trimmed.matches("(0[1-9]|1[0-2])/\\d{2}")) {
            return false;
        }
        int month = Integer.parseInt(trimmed.substring(0, 2));
        int year = 2000 + Integer.parseInt(trimmed.substring(3, 5));
        YearMonth expiry = YearMonth.of(year, month);
        return !expiry.isBefore(now) && !expiry.isAfter(now.plusYears(20));
    }
}
