package com.adoptimizer.validation;

import com.adoptimizer.model.CodedEnum;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The string must be one of the codes of the given {@link CodedEnum} (e.g. {@code "gen-z"}). */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EnumCodeValidator.class)
public @interface EnumCode {

    String message() default "Select a valid option";

    Class<? extends CodedEnum> enumClass();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
