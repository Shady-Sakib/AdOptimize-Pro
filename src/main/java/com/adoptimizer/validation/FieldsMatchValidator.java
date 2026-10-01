package com.adoptimizer.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

import java.util.Objects;

public class FieldsMatchValidator implements ConstraintValidator<FieldsMatch, Object> {

    private String field;
    private String matchField;
    private String message;

    @Override
    public void initialize(FieldsMatch annotation) {
        this.field = annotation.field();
        this.matchField = annotation.matchField();
        this.message = annotation.message();
    }

    @Override
    public boolean isValid(Object target, ConstraintValidatorContext context) {
        if (target == null) {
            return true;
        }
        BeanWrapperImpl wrapper = new BeanWrapperImpl(target);
        Object first = wrapper.getPropertyValue(field);
        Object second = wrapper.getPropertyValue(matchField);
        if (first == null || second == null) {
            // Missing values are reported by @NotBlank on the individual fields.
            return true;
        }
        if (Objects.equals(first, second)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(matchField)
                .addConstraintViolation();
        return false;
    }
}
