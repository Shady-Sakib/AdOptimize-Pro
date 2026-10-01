package com.adoptimizer.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base class for errors that are reported to the browser with a specific HTTP status and message. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
