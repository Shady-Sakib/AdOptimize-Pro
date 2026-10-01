package com.adoptimizer.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Expiry date in {@code MM/YY} format that has not passed and is at most 20 years ahead. */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CardExpiryValidator.class)
public @interface CardExpiry {

    String message() default "Enter a valid expiry date (MM/YY) that has not passed";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
