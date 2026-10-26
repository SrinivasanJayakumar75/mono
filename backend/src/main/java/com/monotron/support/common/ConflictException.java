package com.monotron.support.common;

import org.springframework.http.HttpStatus;

/** The request conflicts with the current state of the resource. */
public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
