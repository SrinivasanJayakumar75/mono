package com.monotron.support.common;

import org.springframework.http.HttpStatus;

/** The caller is authenticated but not allowed to perform this operation. */
public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
