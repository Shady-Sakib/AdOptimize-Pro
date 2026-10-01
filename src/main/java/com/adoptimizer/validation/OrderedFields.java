package com.adoptimizer.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint: the value of {@link #first()} must come before the value of {@link #second()}.
 * Works for any {@link Comparable} type such as {@code LocalDate} or {@code Integer}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OrderedFieldsValidator.class)
public @interface OrderedFields {

    String message() default "Values are in the wrong order";

    String first();

    String second();

    /** When {@code true} the values may not be equal. */
    boolean strict() default true;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
