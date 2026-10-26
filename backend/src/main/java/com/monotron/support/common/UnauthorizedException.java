package com.monotron.support.common;

import org.springframework.http.HttpStatus;

/** The supplied credentials are missing, invalid or expired. */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
