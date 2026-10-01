package com.adoptimizer.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Business-rule validation failure tied to a specific form field
 * (for example "email already registered" or "budget exceeds available funds").
 * Rendered in exactly the same JSON shape as Bean Validation errors.
 */
@Getter
public class FieldValidationException extends ApiException {

    private final Map<String, String> fieldErrors;

    public FieldValidationException(String field, String message) {
        this(HttpStatus.BAD_REQUEST, field, message);
    }

    public FieldValidationException(HttpStatus status, String field, String message) {
        super(status, message);
        this.fieldErrors = new LinkedHashMap<>();
        this.fieldErrors.put(field, message);
    }
}
