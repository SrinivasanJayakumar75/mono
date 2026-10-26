package com.monotron.support.common;

import org.springframework.http.HttpStatus;

/**
 * Base class for all expected, business level failures. Carries the HTTP status
 * that should be surfaced to the client.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
