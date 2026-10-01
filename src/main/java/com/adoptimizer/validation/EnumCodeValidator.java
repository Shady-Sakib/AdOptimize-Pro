package com.adoptimizer.validation;

import com.adoptimizer.model.CodedEnum;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class EnumCodeValidator implements ConstraintValidator<EnumCode, String> {

    private Set<String> allowedCodes;

    @Override
    public void initialize(EnumCode annotation) {
        CodedEnum[] constants = annotation.enumClass().getEnumConstants();
        this.allowedCodes = Arrays.stream(constants)
                .map(CodedEnum::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // @NotBlank reports missing values
        }
        return allowedCodes.contains(value.trim().toLowerCase());
    }
}
