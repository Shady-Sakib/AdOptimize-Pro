package com.adoptimizer.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

public class OrderedFieldsValidator implements ConstraintValidator<OrderedFields, Object> {

    private String first;
    private String second;
    private boolean strict;
    private String message;

    @Override
    public void initialize(OrderedFields annotation) {
        this.first = annotation.first();
        this.second = annotation.second();
        this.strict = annotation.strict();
        this.message = annotation.message();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean isValid(Object target, ConstraintValidatorContext context) {
        if (target == null) {
            return true;
        }
        BeanWrapperImpl wrapper = new BeanWrapperImpl(target);
        Object low = wrapper.getPropertyValue(first);
        Object high = wrapper.getPropertyValue(second);
        if (!(low instanceof Comparable lowValue) || high == null) {
            return true;
        }
        int comparison = lowValue.compareTo(high);
        boolean valid = strict ? comparison < 0 : comparison <= 0;
        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode(second)
                    .addConstraintViolation();
        }
        return valid;
    }
}
