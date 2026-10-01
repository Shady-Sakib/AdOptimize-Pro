package com.adoptimizer.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Comma-separated keyword list: at most {@link #max()} keywords, each 2–30 characters of
 * letters, digits, spaces, hyphens, ampersands or apostrophes.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = KeywordListValidator.class)
public @interface KeywordList {

    String message() default "Use up to {max} comma-separated keywords of 2–30 letters, numbers or spaces";

    int max() default 20;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
