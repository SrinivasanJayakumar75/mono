package com.monotron.support.common;

import org.springframework.http.HttpStatus;

/** The request was malformed or semantically invalid. */
public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
