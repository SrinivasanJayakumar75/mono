package com.monotron.support.common;

import org.springframework.http.HttpStatus;

/** The requested resource does not exist (or is not visible to this tenant). */
public class NotFoundException extends ApiException {
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
