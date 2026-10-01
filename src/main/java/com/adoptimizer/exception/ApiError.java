package com.adoptimizer.exception;

import java.time.LocalDateTime;
import java.util.Map;

/** JSON body returned for every failed API call. */
public record ApiError(
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors,
        LocalDateTime timestamp) {

    public static ApiError of(int status, String error, String message, Map<String, String> fieldErrors) {
        return new ApiError(status, error, message, fieldErrors == null ? Map.of() : fieldErrors,
                LocalDateTime.now().withNano(0));
    }
}
