package com.adoptimizer.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Class-level constraint: two properties must hold equal values (e.g. password and confirmation). */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FieldsMatchValidator.class)
public @interface FieldsMatch {

    String message() default "Values do not match";

    /** Property holding the original value. */
    String field();

    /** Property that must equal {@link #field()}; the error is reported on this property. */
    String matchField();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
