package com.adoptimizer.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Card number of 13–19 digits (spaces allowed) that passes the Luhn checksum. */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LuhnCardNumberValidator.class)
public @interface LuhnCardNumber {

    String message() default "Enter a valid card number";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
